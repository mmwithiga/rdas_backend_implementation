package com.rdas.service;

import com.rdas.dto.CountryFilterRequest;
import com.rdas.dto.CountryResponse;
import com.rdas.dto.PagedResponse;
import com.rdas.integration.CountryInfoSoapClient;
import com.rdas.model.ContinentData;
import com.rdas.model.CountryData;
import com.rdas.model.CurrencyData;
import com.rdas.model.LanguageData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CountryServiceTest {

    @Mock
    private CountryInfoSoapClient soapClient;

    @InjectMocks
    private CountryService countryService;

    private List<CountryData> testCountries;

    @BeforeEach
    void setUp() {
        testCountries = List.of(
                new CountryData("KE", "Kenya", "Nairobi", "AF", "Africa",
                        "KES", "Kenyan Shilling", "EN", "English",
                        "https://flag.ke", "+254"),
                new CountryData("NG", "Nigeria", "Abuja", "AF", "Africa",
                        "NGN", "Nigerian Naira", "EN", "English",
                        "https://flag.ng", "+234"),
                new CountryData("US", "United States", "Washington", "NA", "North America",
                        "USD", "US Dollar", "EN", "English",
                        "https://flag.us", "+1"),
                new CountryData("DE", "Germany", "Berlin", "EU", "Europe",
                        "EUR", "Euro", "DE", "German",
                        "https://flag.de", "+49")
        );
    }

    @Test
    @DisplayName("Filter by name — partial match, case-insensitive")
    void searchByName() {
        stubSoap();
        var filter = new CountryFilterRequest("ken", null, null, null);
        var pageable = PageRequest.of(0, 20, Sort.by("name").ascending());

        PagedResponse<CountryResponse> result = countryService.searchCountries(filter, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).isoCode()).isEqualTo("KE");
    }

    @Test
    @DisplayName("Filter by continent — exact code match")
    void searchByContinent() {
        stubSoap();
        var filter = new CountryFilterRequest(null, "AF", null, null);
        var pageable = PageRequest.of(0, 20, Sort.by("name").ascending());

        PagedResponse<CountryResponse> result = countryService.searchCountries(filter, pageable);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content()).extracting(CountryResponse::isoCode)
                .containsExactlyInAnyOrder("KE", "NG");
    }

    @Test
    @DisplayName("Filter by currency code")
    void searchByCurrency() {
        stubSoap();
        var filter = new CountryFilterRequest(null, null, "USD", null);
        var pageable = PageRequest.of(0, 20);

        PagedResponse<CountryResponse> result = countryService.searchCountries(filter, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).isoCode()).isEqualTo("US");
    }

    @Test
    @DisplayName("Pagination — respects page size")
    void paginationPageSize() {
        stubSoap();
        var filter = new CountryFilterRequest(null, null, null, null);
        var pageable = PageRequest.of(0, 2, Sort.by("name").ascending());

        PagedResponse<CountryResponse> result = countryService.searchCountries(filter, pageable);

        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(4);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.first()).isTrue();
        assertThat(result.last()).isFalse();
    }

    @Test
    @DisplayName("Sorting by name ascending")
    void sortByNameAscending() {
        stubSoap();
        var filter = new CountryFilterRequest(null, null, null, null);
        var pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "name"));

        PagedResponse<CountryResponse> result = countryService.searchCountries(filter, pageable);

        assertThat(result.content()).extracting(CountryResponse::name)
                .isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    }

    @Test
    @DisplayName("No filters returns all countries")
    void noFiltersReturnsAll() {
        stubSoap();
        var filter = new CountryFilterRequest(null, null, null, null);
        var pageable = PageRequest.of(0, 20);

        PagedResponse<CountryResponse> result = countryService.searchCountries(filter, pageable);

        assertThat(result.totalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("Combined filters — continent + currency")
    void combinedFilters() {
        stubSoap();
        var filter = new CountryFilterRequest(null, "AF", "KES", null);
        var pageable = PageRequest.of(0, 20);

        PagedResponse<CountryResponse> result = countryService.searchCountries(filter, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).isoCode()).isEqualTo("KE");
    }

    // ─── Stubs ────────────────────────────────────────────────────────────────

    private void stubSoap() {
        when(soapClient.listContinents()).thenReturn(List.of(
                new ContinentData("AF", "Africa"),
                new ContinentData("NA", "North America"),
                new ContinentData("EU", "Europe")
        ));
        when(soapClient.listCurrencies()).thenReturn(List.of(
                new CurrencyData("KES", "Kenyan Shilling"),
                new CurrencyData("USD", "US Dollar")
        ));
        when(soapClient.listLanguages()).thenReturn(List.of(
                new LanguageData("EN", "English"),
                new LanguageData("DE", "German")
        ));
        // listCountriesByContinent returns subset
        when(soapClient.listCountriesByContinent("AF")).thenReturn(
                testCountries.stream().filter(c -> "AF".equals(c.continentCode())).toList());
        when(soapClient.listCountriesByContinent("NA")).thenReturn(
                testCountries.stream().filter(c -> "NA".equals(c.continentCode())).toList());
        when(soapClient.listCountriesByContinent("EU")).thenReturn(
                testCountries.stream().filter(c -> "EU".equals(c.continentCode())).toList());
        // Enrichment stubs
        when(soapClient.getCapitalCity(anyString())).thenReturn("Capital");
        when(soapClient.getCountryFlag(anyString())).thenReturn("https://flag.example.com");
        when(soapClient.getPhoneCode(anyString())).thenReturn("+000");
        when(soapClient.getCountryCurrency(anyString())).thenReturn(new CurrencyData("XXX", "Test Currency"));
    }
}
