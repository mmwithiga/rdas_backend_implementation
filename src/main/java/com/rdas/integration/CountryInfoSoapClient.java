package com.rdas.integration;

import com.rdas.exception.SoapServiceUnavailableException;
import com.rdas.model.ContinentData;
import com.rdas.model.CountryData;
import com.rdas.model.CurrencyData;
import com.rdas.model.LanguageData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Low-level SOAP client for CountryInfoService.
 * Sends raw SOAP envelopes and parses XML responses.
 * All public methods are retryable with exponential backoff.
 */
@Component
@Slf4j
public class CountryInfoSoapClient {

    private static final String SOAP_NS = "http://www.oorsprong.org/websamples.countryinfo";

    @Value("${soap.countryinfo.endpoint-url}")
    private String endpointUrl;

    @Value("${soap.countryinfo.connect-timeout:10000}")
    private int connectTimeout;

    @Value("${soap.countryinfo.read-timeout:15000}")
    private int readTimeout;

    private HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .build();
    }

    // ─── Public Operations ────────────────────────────────────────────────────

    @Retryable(retryFor = SoapServiceUnavailableException.class,
               maxAttempts = 3,
               backoff = @Backoff(delay = 1000, multiplier = 2))
    public List<ContinentData> listContinents() {
        log.debug("SOAP: ListOfContinentsByName");
        String body = """
                <ListOfContinentsByName xmlns="%s"/>
                """.formatted(SOAP_NS);
        Document doc = call("ListOfContinentsByName", body);
        List<ContinentData> continents = new ArrayList<>();
        NodeList nodes = doc.getElementsByTagNameNS(SOAP_NS, "tContinent");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String code = getText(el, "sCode");
            String name = getText(el, "sName");
            if (code != null && name != null) {
                continents.add(new ContinentData(code, name));
            }
        }
        return continents;
    }

    @Retryable(retryFor = SoapServiceUnavailableException.class,
               maxAttempts = 3,
               backoff = @Backoff(delay = 1000, multiplier = 2))
    public List<CurrencyData> listCurrencies() {
        log.debug("SOAP: ListOfCurrenciesByName");
        String body = """
                <ListOfCurrenciesByName xmlns="%s"/>
                """.formatted(SOAP_NS);
        Document doc = call("ListOfCurrenciesByName", body);
        List<CurrencyData> currencies = new ArrayList<>();
        NodeList nodes = doc.getElementsByTagNameNS(SOAP_NS, "tCurrency");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String code = getText(el, "sISOCode");
            String name = getText(el, "sName");
            if (code != null && name != null) {
                currencies.add(new CurrencyData(code, name));
            }
        }
        return currencies;
    }

    @Retryable(retryFor = SoapServiceUnavailableException.class,
               maxAttempts = 3,
               backoff = @Backoff(delay = 1000, multiplier = 2))
    public List<LanguageData> listLanguages() {
        log.debug("SOAP: ListOfLanguagesByName");
        String body = """
                <ListOfLanguagesByName xmlns="%s"/>
                """.formatted(SOAP_NS);
        Document doc = call("ListOfLanguagesByName", body);
        List<LanguageData> languages = new ArrayList<>();
        NodeList nodes = doc.getElementsByTagNameNS(SOAP_NS, "tLanguage");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String code = getText(el, "sISOCode");
            String name = getText(el, "sName");
            if (code != null && name != null) {
                languages.add(new LanguageData(code, name));
            }
        }
        return languages;
    }

    @Retryable(retryFor = SoapServiceUnavailableException.class,
               maxAttempts = 3,
               backoff = @Backoff(delay = 1000, multiplier = 2))
    public List<CountryData> listCountriesByContinent(String continentCode) {
        log.debug("SOAP: ListOfCountryNamesGroupedByContinent for {}", continentCode);
        String body = """
                <ListOfCountryNamesGroupedByContinent xmlns="%s"/>
                """.formatted(SOAP_NS);
        Document doc = call("ListOfCountryNamesGroupedByContinent", body);
        List<CountryData> countries = new ArrayList<>();

        NodeList continentNodes = doc.getElementsByTagNameNS(SOAP_NS, "tCountryCodeAndNameGroupedByContinent");
        for (int i = 0; i < continentNodes.getLength(); i++) {
            Element continentEl = (Element) continentNodes.item(i);
            String cCode = getText(continentEl, "sCode");
            String cName = getText(continentEl, "sName");

            if (continentCode != null && !continentCode.equalsIgnoreCase(cCode)) continue;

            NodeList countryNodes = continentEl.getElementsByTagNameNS(SOAP_NS, "tCountryCodeAndName");
            for (int j = 0; j < countryNodes.getLength(); j++) {
                Element countryEl = (Element) countryNodes.item(j);
                String isoCode = getText(countryEl, "sISOCode");
                String name    = getText(countryEl, "sName");
                if (isoCode != null && name != null) {
                    // Basic record — capital/currency/language fetched separately if needed
                    countries.add(new CountryData(
                            isoCode, name, null, cCode, cName,
                            null, null, null, null, null, null));
                }
            }
        }
        return countries;
    }

    @Retryable(retryFor = SoapServiceUnavailableException.class,
               maxAttempts = 3,
               backoff = @Backoff(delay = 1000, multiplier = 2))
    public String getCapitalCity(String isoCode) {
        log.debug("SOAP: CapitalCity for {}", isoCode);
        String body = """
                <CapitalCity xmlns="%s">
                    <sCountryISOCode>%s</sCountryISOCode>
                </CapitalCity>
                """.formatted(SOAP_NS, isoCode);
        Document doc = call("CapitalCity", body);
        NodeList nodes = doc.getElementsByTagNameNS(SOAP_NS, "CapitalCityResult");
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent() : null;
    }

    @Retryable(retryFor = SoapServiceUnavailableException.class,
               maxAttempts = 3,
               backoff = @Backoff(delay = 1000, multiplier = 2))
    public String getCountryFlag(String isoCode) {
        log.debug("SOAP: CountryFlag for {}", isoCode);
        String body = """
                <CountryFlag xmlns="%s">
                    <sCountryISOCode>%s</sCountryISOCode>
                </CountryFlag>
                """.formatted(SOAP_NS, isoCode);
        Document doc = call("CountryFlag", body);
        NodeList nodes = doc.getElementsByTagNameNS(SOAP_NS, "CountryFlagResult");
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent() : null;
    }

    @Retryable(retryFor = SoapServiceUnavailableException.class,
               maxAttempts = 3,
               backoff = @Backoff(delay = 1000, multiplier = 2))
    public String getPhoneCode(String isoCode) {
        log.debug("SOAP: CountryIntPhoneCode for {}", isoCode);
        String body = """
                <CountryIntPhoneCode xmlns="%s">
                    <sCountryISOCode>%s</sCountryISOCode>
                </CountryIntPhoneCode>
                """.formatted(SOAP_NS, isoCode);
        Document doc = call("CountryIntPhoneCode", body);
        NodeList nodes = doc.getElementsByTagNameNS(SOAP_NS, "CountryIntPhoneCodeResult");
        return nodes.getLength() > 0 ? "+" + nodes.item(0).getTextContent() : null;
    }

    @Retryable(retryFor = SoapServiceUnavailableException.class,
               maxAttempts = 3,
               backoff = @Backoff(delay = 1000, multiplier = 2))
    public CurrencyData getCountryCurrency(String isoCode) {
        log.debug("SOAP: CountryCurrency for {}", isoCode);
        String body = """
                <CountryCurrency xmlns="%s">
                    <sCountryISOCode>%s</sCountryISOCode>
                </CountryCurrency>
                """.formatted(SOAP_NS, isoCode);
        Document doc = call("CountryCurrency", body);
        NodeList nodes = doc.getElementsByTagNameNS(SOAP_NS, "CountryCurrencyResult");
        if (nodes.getLength() == 0) return null;
        Element el = (Element) nodes.item(0);
        String code = getText(el, "sISOCode");
        String name = getText(el, "sName");
        return (code != null) ? new CurrencyData(code, name) : null;
    }

    @Retryable(retryFor = SoapServiceUnavailableException.class,
               maxAttempts = 3,
               backoff = @Backoff(delay = 1000, multiplier = 2))
    public List<String> getCountriesUsingCurrency(String currencyIso) {
        log.debug("SOAP: CountriesUsingCurrency for {}", currencyIso);
        String body = """
                <CountriesUsingCurrency xmlns="%s">
                    <sISOCurrencyCode>%s</sISOCurrencyCode>
                </CountriesUsingCurrency>
                """.formatted(SOAP_NS, currencyIso);
        Document doc = call("CountriesUsingCurrency", body);
        List<String> codes = new ArrayList<>();
        NodeList nodes = doc.getElementsByTagNameNS(SOAP_NS, "tCountryCodeAndName");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String code = getText(el, "sISOCode");
            if (code != null) codes.add(code);
        }
        return codes;
    }

    // ─── Recovery ─────────────────────────────────────────────────────────────

    @Recover
    public List<ContinentData> recoverContinents(SoapServiceUnavailableException ex) {
        log.error("SOAP unavailable after retries for listContinents: {}", ex.getMessage());
        throw ex;
    }

    @Recover
    public List<CurrencyData> recoverCurrencies(SoapServiceUnavailableException ex) {
        log.error("SOAP unavailable after retries for listCurrencies: {}", ex.getMessage());
        throw ex;
    }

    @Recover
    public List<LanguageData> recoverLanguages(SoapServiceUnavailableException ex) {
        log.error("SOAP unavailable after retries for listLanguages: {}", ex.getMessage());
        throw ex;
    }

    @Recover
    public List<CountryData> recoverCountries(SoapServiceUnavailableException ex, String continentCode) {
        log.error("SOAP unavailable after retries for listCountriesByContinent: {}", ex.getMessage());
        throw ex;
    }

    // ─── HTTP + XML Helpers ───────────────────────────────────────────────────

    private Document call(String action, String bodyContent) {
        String envelope = """
                <?xml version="1.0" encoding="utf-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                    <soap:Body>
                        %s
                    </soap:Body>
                </soap:Envelope>
                """.formatted(bodyContent);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .header("SOAPAction", '\"' + SOAP_NS + "/" + action + '\"')
                    .timeout(Duration.ofMillis(readTimeout))
                    .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new SoapServiceUnavailableException(
                        "SOAP call '%s' returned HTTP %d".formatted(action, response.statusCode()));
            }

            return parseXml(response.body());

        } catch (SoapServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new SoapServiceUnavailableException(
                    "SOAP call '%s' failed: %s".formatted(action, e.getMessage()), e);
        }
    }

    private Document parseXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new SoapServiceUnavailableException("Failed to parse SOAP response", e);
        }
    }

    private String getText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagNameNS(SOAP_NS, tagName);
        if (nodes.getLength() == 0) {
            // Try without namespace
            nodes = parent.getElementsByTagName(tagName);
        }
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent().trim() : null;
    }
}
