package com.flowguard.resource;

import com.flowguard.dto.FinancialKpisDto;
import com.flowguard.service.FinancialKpiService;
import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

@Path("/kpis")
@Authenticated
@RunOnVirtualThread
@Produces(MediaType.APPLICATION_JSON)
public class FinancialKpiResource {

    @Inject
    JsonWebToken jwt;

    @Inject
    FinancialKpiService financialKpiService;

    @GET
    public FinancialKpisDto getKpis() {
        UUID userId = UUID.fromString(jwt.getSubject());
        return financialKpiService.computeKpis(userId);
    }
}
