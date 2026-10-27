package org.wayfarer.atlas.domain;

/** Provenance for a rule; titles and dates describe the cited source, not a guarantee of entry. */
public final class RuleCitation {
    private final String nameEn, nameRu, url, checkedAt;
    private final boolean official;
    public RuleCitation(String nameEn, String nameRu, String url, String checkedAt, boolean official) {
        this.nameEn = nameEn; this.nameRu = nameRu; this.url = url;
        this.checkedAt = checkedAt; this.official = official;
    }
    public String getNameEn() { return nameEn; }
    public String getNameRu() { return nameRu; }
    public String getUrl() { return url; }
    public String getCheckedAt() { return checkedAt; }
    public boolean isOfficial() { return official; }
}
