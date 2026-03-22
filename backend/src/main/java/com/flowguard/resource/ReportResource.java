package com.flowguard.resource;

import com.flowguard.service.FinancialReportService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.LocalDate;
import java.util.UUID;

@Path("/reports")
@RolesAllowed("user")
public class ReportResource {

    @Inject FinancialReportService reportService;
    @Inject JsonWebToken jwt;

    @GET
    @Path("/financial")
    @Produces("application/pdf")
    @RunOnVirtualThread
    public Response downloadFinancialReport() {
        UUID userId = UUID.fromString(jwt.getSubject());
        byte[] pdf = reportService.generateReport(userId);
        String filename = "rapport-financier-" + LocalDate.now() + ".pdf";
        return Response.ok(pdf)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .header("Content-Length", pdf.length)
                .build();
    }
}
