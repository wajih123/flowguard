package com.flowguard.resource;

import com.flowguard.dto.SectorBenchmarkDto;
import com.flowguard.dto.UserBenchmarkDto;
import com.flowguard.service.SectorBenchmarkService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.UUID;

@Path("/benchmarks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class BenchmarkResource {

    @Inject SectorBenchmarkService benchmarkService;
    @Inject JsonWebToken jwt;

    @GET
    @Path("/sectors")
    @RunOnVirtualThread
    public List<String> getSectors() {
        return benchmarkService.getAvailableSectors();
    }

    @GET
    @Path("/{sector}/{companySize}")
    @RunOnVirtualThread
    public List<SectorBenchmarkDto> getBenchmarks(
            @PathParam("sector") String sector,
            @PathParam("companySize") String companySize) {
        return benchmarkService.getForSector(sector, companySize);
    }

    @GET
    @Path("/compare/{sector}/{companySize}")
    @RunOnVirtualThread
    public List<UserBenchmarkDto> compare(
            @PathParam("sector") String sector,
            @PathParam("companySize") String companySize) {
        return benchmarkService.compareUser(UUID.fromString(jwt.getSubject()), sector, companySize);
    }
}
