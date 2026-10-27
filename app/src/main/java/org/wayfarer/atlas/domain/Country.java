package org.wayfarer.atlas.domain;

public final class Country {
    private final String code;
    private final String nameEn;
    private final String nameRu;

    public Country(String code, String nameEn, String nameRu) {
        this.code = code;
        this.nameEn = nameEn;
        this.nameRu = nameRu;
    }

    public String getCode() { return code; }
    public String getNameEn() { return nameEn; }
    public String getNameRu() { return nameRu; }
}
