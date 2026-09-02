package ma.jurika.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void reusesIncomingHeader() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain chain = Mockito.mock(FilterChain.class);
        Mockito.when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("req-abc-123");

        filter.doFilter(request, response, chain);

        Mockito.verify(response).setHeader(CorrelationIdFilter.HEADER, "req-abc-123");
        Mockito.verify(chain).doFilter(request, response);
    }

    @Test
    void generatesUuidWhenHeaderAbsent() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain chain = Mockito.mock(FilterChain.class);
        Mockito.when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn(null);

        filter.doFilter(request, response, chain);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(response).setHeader(Mockito.eq(CorrelationIdFilter.HEADER), captor.capture());
        assertThat(captor.getValue()).hasSize(36).contains("-");
    }

    @Test
    void generatesUuidWhenHeaderBlank() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain chain = Mockito.mock(FilterChain.class);
        Mockito.when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("   ");

        filter.doFilter(request, response, chain);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(response).setHeader(Mockito.eq(CorrelationIdFilter.HEADER), captor.capture());
        assertThat(captor.getValue()).isNotBlank().isNotEqualTo("   ");
    }

    @Test
    void mdcIsClearedAfterRequest() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain chain = Mockito.mock(FilterChain.class);
        Mockito.when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("test");

        filter.doFilter(request, response, chain);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
