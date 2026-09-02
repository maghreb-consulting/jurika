package ma.jurika.ticket.application;

import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.port.TicketFilter;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SearchTicketsUseCase {

    private final TicketRepository ticketRepository;

    public SearchTicketsUseCase(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    public record Result(List<Ticket> items, long total) {}

    @Transactional(readOnly = true)
    public Result execute(TicketFilter filter, int limit, int offset) {
        TenantContext.set(filter.workspaceId());
        List<Ticket> items = ticketRepository.search(filter, limit, offset);
        long total = ticketRepository.count(filter);
        return new Result(items, total);
    }
}
