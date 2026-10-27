#!/usr/bin/env python3
"""Install an isolated Android build toolchain; never changes shell/system settings."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import stat
import subprocess
import sys
import tarfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / ".tooling"


def download(url, destination, checksum=None, algorithm="sha256", size=None):
    destination = Path(destination)
    if not url.startswith("https://"):
        raise ValueError("Only HTTPS downloads are allowed")
    if destination.exists() and checksum:
        if hashlib.new(algorithm, destination.read_bytes()).hexdigest() == checksum:
            return destination
    if size and checksum:
        subprocess.run([sys.executable, str(ROOT / "scripts/download-artifact.py"), url,
                        str(destination), "--size", str(size), "--" + algorithm, checksum], check=True)
        return destination
    part = destination.with_suffix(destination.suffix + ".part")
    print("Downloading " + destination.name, flush=True)
    subprocess.run(["curl", "--fail", "--location", "--proto", "=https", "--proto-redir", "=https",
                    "--connect-timeout", "15", "--max-time", "1200", "--silent", "--show-error",
                    "--retry", "2", "--retry-delay", "1", "--user-agent", "WayfarerAtlas-build/0.1",
                    "--output", str(part), url], check=True)
    if checksum and hashlib.new(algorithm, part.read_bytes()).hexdigest() != checksum:
        part.unlink()
        raise RuntimeError(f"Checksum mismatch: {destination.name}")
    part.replace(destination)
    return destination


def extract(archive, destination):
    destination.mkdir(parents=True, exist_ok=True)
    base = destination.resolve()
    def safe(name):
        target = (destination / name).resolve()
        if target != base and base not in target.parents:
            raise ValueError("Unsafe archive path")
    if zipfile.is_zipfile(archive):
        with zipfile.ZipFile(archive) as zipped:
            for member in zipped.infolist():
                safe(member.filename)
                mode = member.external_attr >> 16
                if stat.S_ISLNK(mode):
                    safe(str(Path(member.filename).parent / zipped.read(member).decode()))
            for member in zipped.infolist():
                target = destination / member.filename
                mode = member.external_attr >> 16
                if stat.S_ISLNK(mode):
                    target.parent.mkdir(parents=True, exist_ok=True)
                    if target.is_symlink() or target.exists():
                        target.unlink()
                    target.symlink_to(zipped.read(member).decode())
                else:
                    zipped.extract(member, destination)
                    if mode & 0o777 and target.is_file():
                        target.chmod(mode & 0o777)
    else:
        with tarfile.open(archive) as packed:
            for member in packed.getmembers():
                safe(member.name)
                if member.issym():
                    safe(str(Path(member.name).parent / member.linkname))
                elif member.islnk():
                    safe(member.linkname)
            packed.extractall(destination)


def install_sdk_archive(manifest, package_path, filename, sdk, os_name, arch, base_url):
    """Install only the checksum-verified archive published in Google's manifest.

    source.properties and package metadata come from the official archive;
    sdkmanager subsequently discovers the installation and handles licenses.
    """
    destination = sdk.joinpath(*package_path.split(";"))
    if destination.exists():
        return
    candidates = []
    for item in ET.parse(manifest).getroot().iter("remotePackage"):
        if item.get("path") != package_path:
            continue
        channel = item.find("channelRef")
        if channel is not None and channel.get("ref") != "channel-0":
            continue
        revision = tuple(int(item.findtext("revision/" + part, "0")) for part in ("major", "minor", "micro"))
        for artifact in item.findall("archives/archive"):
            if artifact.findtext("host-os") not in (None, {"mac": "macosx", "linux": "linux"}[os_name]):
                continue
            if artifact.findtext("host-arch") not in (None, arch):
                continue
            checksum = artifact.find("complete/checksum")
            candidates.append((revision, artifact.findtext("complete/url"), checksum.text,
                               checksum.get("type", "sha1"), int(artifact.findtext("complete/size"))))
    if not candidates:
        raise RuntimeError("Official Android manifest has no stable package: " + package_path)
    _, resource, checksum, algorithm, size = max(candidates)
    archive = download(base_url + resource, TOOLS / filename, checksum, algorithm, size)
    temporary = TOOLS / ("unpack-" + filename)
    if temporary.exists():
        shutil.rmtree(temporary)
    extract(archive, temporary)
    entries = list(temporary.iterdir())
    if len(entries) != 1 or not entries[0].is_dir():
        raise RuntimeError("Unexpected Android package archive layout: " + package_path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(entries[0]), destination)
    temporary.rmdir()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--accept-sdk-licenses", action="store_true",
                        help="Accept the SDK licenses displayed by sdkmanager")
    parser.add_argument("--with-emulator", action="store_true")
    args = parser.parse_args()
    TOOLS.mkdir(exist_ok=True)
    os_name = {"Darwin": "mac", "Linux": "linux"}.get(platform.system())
    if not os_name:
        raise SystemExit("Use Android Studio on Windows; see docs/release")
    arch = "aarch64" if platform.machine() in ("arm64", "aarch64") else "x64"
    java_homes = list((TOOLS / "jdk").glob("**/bin/javac"))
    if not java_homes:
        url = f"https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture={arch}&image_type=jdk&os={os_name}"
        metadata = json.loads(download(url, TOOLS / "jdk-assets.json").read_text())
        package = metadata[0]["binary"]["package"]
        archive = download(package["link"], TOOLS / "jdk.tar.gz", package["checksum"], size=package["size"])
        extract(archive, TOOLS / "jdk")
        java_homes = list((TOOLS / "jdk").glob("**/bin/javac"))
    java_home = java_homes[0].parent.parent
    for executable in (java_home / "bin").iterdir():
        if executable.is_file():
            executable.chmod(executable.stat().st_mode | 0o111)
    gradle = TOOLS / "gradle-8.13" / "bin" / "gradle"
    if not gradle.exists():
        url = "https://services.gradle.org/distributions/gradle-8.13-bin.zip"
        checksum = download(url + ".sha256", TOOLS / "gradle.sha256").read_text().strip().split()[0]
        extract(download(url, TOOLS / "gradle.zip", checksum, size=136983045), TOOLS)
        gradle.chmod(0o755)
    sdk = TOOLS / "android-sdk"
    manifest = TOOLS / "android-repository.xml"
    sdkmanager = sdk / "cmdline-tools" / "latest" / "bin" / "sdkmanager"
    if not sdkmanager.exists():
        manifest = download("https://dl.google.com/android/repository/repository2-3.xml", TOOLS / "android-repository.xml")
        tree = ET.parse(manifest)
        packages = [item for item in tree.getroot().iter() if item.tag.endswith("remotePackage")
                    and item.attrib.get("path", "").startswith("cmdline-tools;")]
        candidates = []
        for item in packages:
            channel = item.find("channelRef")
            if channel is not None and channel.attrib.get("ref") != "channel-0":
                continue
            revision = tuple(int(item.findtext(f"revision/{part}", "0")) for part in ("major", "minor", "micro"))
            for artifact in item.findall("archives/archive"):
                if (artifact.findtext("host-os") == {"mac": "macosx", "linux": "linux"}[os_name]
                        and artifact.findtext("host-arch") in (None, arch)):
                    checksum_node = artifact.find("complete/checksum")
                    candidates.append((revision, artifact.findtext("complete/url"), checksum_node.text,
                                       checksum_node.attrib.get("type", "sha1"), int(artifact.findtext("complete/size"))))
        if not candidates:
            raise RuntimeError("Official Android manifest has no stable command-line tools for this OS")
        _, resource, checksum, algorithm, size = sorted(candidates, reverse=True)[0]
        archive = download("https://dl.google.com/android/repository/" + resource, TOOLS / "commandline-tools.zip", checksum, algorithm, size)
        temporary = TOOLS / "sdk-unpack"
        extract(archive, temporary)
        sdkmanager.parent.parent.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(temporary / "cmdline-tools"), sdkmanager.parent.parent)
        for executable in sdkmanager.parent.iterdir():
            executable.chmod(0o755)
    environment = dict(os.environ, JAVA_HOME=str(java_home), ANDROID_HOME=str(sdk),
                       ANDROID_USER_HOME=str(TOOLS / "android-user"), GRADLE_USER_HOME=str(TOOLS / "gradle-cache"))
    environment["PATH"] = str(java_home / "bin") + os.pathsep + environment.get("PATH", "")
    if not manifest.exists():
        download("https://dl.google.com/android/repository/repository2-3.xml", manifest)
    base_url = "https://dl.google.com/android/repository/"
    for package_path, filename in [("platform-tools", "platform-tools.zip"),
                                   ("platforms;android-36", "platform-36.zip"),
                                   ("build-tools;36.0.0", "build-tools.zip")]:
        install_sdk_archive(manifest, package_path, filename, sdk, os_name, arch, base_url)
    if args.with_emulator:
        image_manifest = download(base_url + "sys-img/aosp_atd/sys-img2-4.xml", TOOLS / "aosp-atd.xml")
        emulator_arch = "arm64-v8a" if arch == "aarch64" else "x86_64"
        install_sdk_archive(image_manifest, "system-images;android-36;aosp_atd;" + emulator_arch,
                            "system-image.zip", sdk, os_name, arch, base_url + "sys-img/aosp_atd/")
    # The 23.x shell shim downloads a separate Android CLI even for --version.
    # The official Java implementation is still bundled and runs on the JDK
    # already installed above; invoking it avoids another unpinned bootstrap.
    sdk_cli = [str(java_home / "bin/java"), "-Dcom.android.sdklib.toolsdir=" + str(sdkmanager.parent.parent),
               "-cp", os.pathsep.join(str(path) for path in (sdkmanager.parent.parent / "lib").rglob("*.jar")),
               "com.android.sdklib.tool.sdkmanager.SdkManagerCli", "--sdk_root=" + str(sdk)]
    if args.accept_sdk_licenses:
        subprocess.run([*sdk_cli, "--licenses"],
                       input="y\n" * 100, text=True, env=environment, check=True)
    # Emulator archives do not carry package.xml, and SDK legacy discovery
    # does not register them from source.properties. Let the official installer
    # create its metadata; otherwise avdmanager rejects a working emulator.
    if args.with_emulator and not (sdk / "emulator/package.xml").exists():
        subprocess.run([*sdk_cli, "emulator"], env=environment, check=True)
    subprocess.run([*sdk_cli, "--list_installed"], env=environment, check=True)
    (ROOT / "local.properties").write_text("sdk.dir=" + str(sdk) + "\n")
    wrapper_project = TOOLS / "wrapper-project"
    wrapper_project.mkdir(exist_ok=True)
    (wrapper_project / "settings.gradle").touch()
    subprocess.run([str(gradle), "wrapper", "--gradle-version", "8.13", "--distribution-type", "bin",
                    "--gradle-distribution-sha256-sum", "20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78"],
                   cwd=wrapper_project, env=environment, check=True)
    for filename in ("gradlew", "gradlew.bat"):
        shutil.copy2(wrapper_project / filename, ROOT / filename)
    (ROOT / "gradle/wrapper").mkdir(parents=True, exist_ok=True)
    for source in (wrapper_project / "gradle/wrapper").iterdir():
        shutil.copy2(source, ROOT / "gradle/wrapper" / source.name)
    print("Toolchain installed in .tooling. Run: bash scripts/gradle.sh testDebugUnitTest lintDebug assembleDebug bundleRelease")


if __name__ == "__main__":
    main()
