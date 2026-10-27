package org.wayfarer.atlas.data;

import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;
import org.wayfarer.atlas.domain.EntryCategory;
import org.wayfarer.atlas.domain.EntryRule;

public final class HongKongCsvParserTest {
    private static final String HEADER = "National of foreign country (territory)/Type of travel document,Visa free period for visiting the Hong Kong Special Administrative Region,Visa requirements\r\n";
    private static final Set<String> CODES = Set.of("RU", "HK", "IN", "AL", "GB");
    private static String mapping(String labels, String entries) {
        return "{\"version\":1,\"destination\":\"HK\",\"sourceUrl\":\"https://www.immd.gov.hk/eng/services/visas/visit-transit/visit-visa-entry-permit.html\",\"sourceLabels\":[" + labels + "],\"entries\":[" + entries + "]}";
    }
    private static String entry(String code, String rows) { return "{\"code\":\""+code+"\",\"rows\":["+rows+"]}"; }
    private static RuleSnapshot parse(String csv, String mapping) {
        return DatasetParser.parseRules(HongKongCsvParser.normalize(csv, mapping, CODES, "2026-10-26"), CODES);
    }
    private static void rejected(String csv, String mapping) {
        try { parse(csv, mapping); fail("Changed/malformed source must not replace the last good snapshot"); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void actualGovernmentCsvAndBundledSnapshotAgreeOnEveryMappedRule() throws Exception {
        java.nio.file.Path data=java.nio.file.Paths.get("data");
        if (!java.nio.file.Files.exists(data.resolve("countries.json"))) data=java.nio.file.Paths.get("..","data");
        java.util.Set<String> codes=new java.util.HashSet<>();
        for (org.wayfarer.atlas.domain.Country country:DatasetParser.parseCountries(read(data.resolve("countries.json")))) codes.add(country.getCode());
        RuleSnapshot actual=DatasetParser.parseRules(HongKongCsvParser.normalize(read(data.resolve("sources/hong-kong-visa-requirements.csv")),read(data.resolve("hong-kong-mapping.json")),codes,"2026-10-26"),codes);
        RuleSnapshot bundled=DatasetParser.parseRules(read(data.resolve("official-hong-kong.json")),codes);
        assertEquals(207,actual.getRules().size()); assertEquals(207,bundled.getRules().size());
        assertEquals("2025-12-31",actual.getSourceAsAt()); assertEquals("2025-12-31",bundled.getSourceAsAt());
        java.util.Map<String,EntryRule> expected=new java.util.HashMap<>(); for(EntryRule r:bundled.getRules()) expected.put(r.getCitizenship(),r);
        for (EntryRule r:actual.getRules()) { EntryRule other=expected.get(r.getCitizenship()); assertNotNull(other); assertEquals(r.getCitizenship(),other.getCategory(),r.getCategory()); assertEquals(other.getMaxStayDays(),r.getMaxStayDays()); }
        assertEquals(EntryCategory.VISA_FREE,expected.get("RU").getCategory()); assertEquals(Integer.valueOf(14),expected.get("RU").getMaxStayDays());
    }
    private static String read(java.nio.file.Path path) throws Exception { return new String(java.nio.file.Files.readAllBytes(path),java.nio.charset.StandardCharsets.UTF_8); }
    @Test public void officialFooterIsAStateDateAndNotAnExtraVisaRow() {
        String canonical=HongKongCsvParser.normalize(HEADER+"Russian Federation,14,Not applicable\r\n(As at 31 December 2025),,\r\n",mapping("\"Russian Federation\"",entry("RU","\"Russian Federation\"")),CODES,"2026-10-26");
        com.google.gson.JsonObject root=com.google.gson.JsonParser.parseString(canonical).getAsJsonObject();
        assertEquals("2025-12-31",root.get("sourceAsAt").getAsString());
        assertEquals(1,DatasetParser.parseRules(canonical,CODES).getRules().size());
    }
    @Test public void numericRussianExemptionIsFourteenDays() {
        RuleSnapshot result = parse(HEADER+"Russian Federation,14,Not applicable\r\n",mapping("\"Russian Federation\"",entry("RU","\"Russian Federation\"")));
        assertEquals(1,result.getRules().size());
        EntryRule rule=result.getRules().get(0);
        assertEquals(EntryCategory.VISA_FREE,rule.getCategory());
        assertEquals(Integer.valueOf(14),rule.getMaxStayDays());
        assertEquals("HK",rule.getDestination());
    }
    @Test public void bomQuotedCommasAndCrLfKeepColumnsAligned() {
        String extra="Britain (British Overseas Territories citizens, British Overseas citizens, British subjects and British Protected persons)";
        String mapping=mapping("\"Russian Federation\",\""+extra+"\"",entry("RU","\"Russian Federation\""));
        RuleSnapshot result=parse("\ufeff"+HEADER+"Russian Federation,14,Not applicable\r\n\""+extra+"\",90,Not applicable\r\n",mapping);
        assertEquals(1,result.getRules().size());
        assertEquals(Integer.valueOf(14),result.getRules().get(0).getMaxStayDays());
    }
    @Test public void indiaPreArrivalRegistrationIsAuthorizationNotUnconditionalVisaFree() {
        EntryRule rule=parse(HEADER+"India,14,Indian nationals are required to apply for and successfully complete pre-arrival registration online before they can visit the HKSAR visa free.\r\n",mapping("\"India\"",entry("IN","\"India\""))).getRules().get(0);
        assertEquals(EntryCategory.ETA,rule.getCategory());
        assertEquals(Integer.valueOf(14),rule.getMaxStayDays());
    }
    @Test public void explicitPriorVisaIsRequiredAndHasNoFreeStayLimit() {
        EntryRule rule=parse(HEADER+"Russian Federation,Not applicable,All nationals are required to hold a valid visa for the HKSAR for whatever purpose\r\n",mapping("\"Russian Federation\"",entry("RU","\"Russian Federation\""))).getRules().get(0);
        assertEquals(EntryCategory.VISA_REQUIRED,rule.getCategory()); assertNull(rule.getMaxStayDays());
    }
    @Test public void newPassportSubtypeRowCannotBeSilentlyIgnored() {
        rejected(HEADER+"Russian Federation,14,Not applicable\r\nRussian Federation (new subtype),Not applicable,All nationals are required to hold a valid visa\r\n",mapping("\"Russian Federation\"",entry("RU","\"Russian Federation\"")));
    }
    @Test public void missingRowAndDuplicateRowsAreRejected() {
        String m=mapping("\"Russian Federation\"",entry("RU","\"Russian Federation\""));
        rejected(HEADER,m);rejected(HEADER+"Russian Federation,14,Not applicable\r\nRussian Federation,30,Not applicable\r\n",m);
    }
    @Test public void brokenQuotingAndChangedHeaderAreRejected() {
        String m=mapping("\"Russian Federation\"",entry("RU","\"Russian Federation\""));
        rejected(HEADER+"\"Russian Federation,14,Not applicable\r\n",m);
        rejected("Country,Days,Visa\r\nRussian Federation,14,Not applicable\r\n",m);
    }
    @Test public void unrecognizedRequirementCannotBecomeVisaFree() {
        EntryRule rule=parse(HEADER+"Russian Federation,Not applicable,Consult the authority\r\n",mapping("\"Russian Federation\"",entry("RU","\"Russian Federation\""))).getRules().get(0);
        assertEquals(EntryCategory.UNKNOWN,rule.getCategory());assertNull(rule.getMaxStayDays());
    }
}
