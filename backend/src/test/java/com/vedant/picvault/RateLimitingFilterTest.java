package com.vedant.picvault;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.vedant.picvault.config.RateLimitingFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitingFilterTest {

    @Test
    void allowsRequestsWithinTheLimit() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 20; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/images");
            request.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isNotEqualTo(429);
        }
        verify(chain, times(20)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blocksTheRequestOnceTheBucketIsExhausted() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse lastResponse = null;

        for (int i = 0; i < 21; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/images");
            request.setRemoteAddr("10.0.0.2");
            lastResponse = new MockHttpServletResponse();
            filter.doFilter(request, lastResponse, chain);
        }

        assertThat(lastResponse.getStatus()).isEqualTo(429);
        assertThat(lastResponse.getContentAsString()).contains("{\"error\":\"too many requests.\"}");
        verify(chain, times(20)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void tracksSeparateBucketsPerIp() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest requestA = new MockHttpServletRequest("GET", "/api/images");
        requestA.setRemoteAddr("10.0.0.3");
        MockHttpServletRequest requestB = new MockHttpServletRequest("GET", "/api/images");
        requestB.setRemoteAddr("10.0.0.4");

        filter.doFilter(requestA, new MockHttpServletResponse(), chain);
        filter.doFilter(requestB, new MockHttpServletResponse(), chain);

        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsNonApiPaths() {
        RateLimitingFilter filter = new RateLimitingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        try {
            assertThat(filter.shouldNotFilter(request)).isTrue();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
}
