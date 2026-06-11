package com.rdas.controller;

import com.rdas.dto.CountryResponse;
import com.rdas.dto.PagedResponse;
import com.rdas.service.CountryService;
import com.rdas.dto.CountryFilterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CountryController.class)
class CountryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CountryService countryService;

    @Test
    void searchCountries_returnsPagedResponse() throws Exception {
        var response = new CountryResponse("KE", "Kenya", "Nairobi",
                "AF", "Africa", "KES", "Kenyan Shilling",
                "EN", "English", "https://flag.ke", "+254");
        var paged = new PagedResponse<>(List.of(response), 1L, 1, 0, 20, true, true);

        when(countryService.searchCountries(any(CountryFilterRequest.class), any(Pageable.class)))
                .thenReturn(paged);

        mockMvc.perform(get("/api/v1/countries?name=Kenya"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].isoCode").value("KE"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.first").value(true));
    }

    @Test
    void searchCountries_invalidContinentCode_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/countries?continent=INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCountry_invalidIsoCode_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/countries/TOOLONG"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCurrencyCountries_invalidCode_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/currencies/TOOLONG/countries"))
                .andExpect(status().isBadRequest());
    }
}
