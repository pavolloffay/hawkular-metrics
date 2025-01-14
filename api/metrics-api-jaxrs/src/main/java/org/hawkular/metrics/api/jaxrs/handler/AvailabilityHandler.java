/*
 * Copyright 2014-2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.metrics.api.jaxrs.handler;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import static org.hawkular.metrics.api.jaxrs.filter.TenantFilter.TENANT_HEADER_NAME;
import static org.hawkular.metrics.api.jaxrs.util.ApiUtils.badRequest;
import static org.hawkular.metrics.api.jaxrs.util.ApiUtils.noContent;
import static org.hawkular.metrics.api.jaxrs.util.ApiUtils.serverError;
import static org.hawkular.metrics.model.MetricType.AVAILABILITY;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hawkular.metrics.api.jaxrs.handler.observer.MetricCreatedObserver;
import org.hawkular.metrics.api.jaxrs.handler.observer.ResultSetObserver;
import org.hawkular.metrics.api.jaxrs.util.ApiUtils;
import org.hawkular.metrics.core.service.Functions;
import org.hawkular.metrics.core.service.MetricsService;
import org.hawkular.metrics.core.service.Order;
import org.hawkular.metrics.model.ApiError;
import org.hawkular.metrics.model.AvailabilityType;
import org.hawkular.metrics.model.Buckets;
import org.hawkular.metrics.model.DataPoint;
import org.hawkular.metrics.model.Metric;
import org.hawkular.metrics.model.MetricId;
import org.hawkular.metrics.model.MetricType;
import org.hawkular.metrics.model.param.BucketConfig;
import org.hawkular.metrics.model.param.Duration;
import org.hawkular.metrics.model.param.Tags;
import org.hawkular.metrics.model.param.TimeRange;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import rx.Observable;

/**
 * @author Stefan Negrea
 *
 */
@Path("/availability")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Api(tags = "Availability")
public class AvailabilityHandler {

    @Inject
    private MetricsService metricsService;

    @HeaderParam(TENANT_HEADER_NAME)
    private String tenantId;

    @POST
    @Path("/")
    @ApiOperation(value = "Create availability metric.", notes = "Same notes as creating gauge metric apply.")
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "Metric created successfully"),
            @ApiResponse(code = 400, message = "Missing or invalid payload", response = ApiError.class),
            @ApiResponse(code = 409, message = "Availability metric with given id already exists",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Metric creation failed due to an unexpected error",
                    response = ApiError.class)
    })
    public void createAvailabilityMetric(
            @Suspended final AsyncResponse asyncResponse,
            @ApiParam(required = true) Metric<AvailabilityType> metric,
            @Context UriInfo uriInfo
    ) {
        if (metric.getType() != null
                && MetricType.UNDEFINED != metric.getType()
                && MetricType.AVAILABILITY != metric.getType()) {
            asyncResponse.resume(badRequest(new ApiError("Metric type does not match " + MetricType
                    .AVAILABILITY.getText())));
        }
        URI location = uriInfo.getBaseUriBuilder().path("/availability/{id}").build(metric.getId());
        metric = new Metric<>(
                new MetricId<>(tenantId, AVAILABILITY, metric.getMetricId().getName()), metric.getTags(),
                metric.getDataRetention());
        metricsService.createMetric(metric).subscribe(new MetricCreatedObserver(asyncResponse, location));
    }

    @GET
    @Path("/")
    @ApiOperation(value = "Find tenant's metric definitions.",
                    notes = "Does not include any metric values. ",
                    response = Metric.class, responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved at least one metric definition."),
            @ApiResponse(code = 204, message = "No metrics found."),
            @ApiResponse(code = 400, message = "Invalid type parameter type.", response = ApiError.class),
            @ApiResponse(code = 500, message = "Failed to retrieve metrics due to unexpected error.",
                    response = ApiError.class)
    })
    public void findAvailabilityMetrics(
            @Suspended AsyncResponse asyncResponse,
            @ApiParam(value = "List of tags filters", required = false) @QueryParam("tags") Tags tags) {

        Observable<Metric<AvailabilityType>> metricObservable = (tags == null)
                ? metricsService.findMetrics(tenantId, AVAILABILITY)
                : metricsService.findMetricsWithFilters(tenantId, AVAILABILITY, tags.getTags());

        metricObservable
                .toList()
                .map(ApiUtils::collectionToResponse)
                .subscribe(asyncResponse::resume, t -> {
                    if (t instanceof PatternSyntaxException) {
                        asyncResponse.resume(badRequest(t));
                    } else {
                        asyncResponse.resume(serverError(t));
                    }
                });
    }

    @GET
    @Path("/{id}")
    @ApiOperation(value = "Retrieve single metric definition.", response = Metric.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Metric's definition was successfully retrieved."),
            @ApiResponse(code = 204, message = "Query was successful, but no metrics definition is set."),
            @ApiResponse(code = 500, message = "Unexpected error occurred while fetching metric's definition.",
                         response = ApiError.class) })
    public void getAvailabilityMetric(@Suspended final AsyncResponse asyncResponse, @PathParam("id") String id) {

        metricsService.findMetric(new MetricId<>(tenantId, AVAILABILITY, id))
                .map(metricDef -> Response.ok(metricDef).build())
                .switchIfEmpty(Observable.just(noContent()))
                .subscribe(asyncResponse::resume, t -> asyncResponse.resume(serverError(t)));
    }

    @GET
    @Path("/{id}/tags")
    @ApiOperation(value = "Retrieve tags associated with the metric definition.", response = String.class,
                  responseContainer = "Map")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Metric's tags were successfully retrieved."),
            @ApiResponse(code = 204, message = "Query was successful, but no metrics were found."),
            @ApiResponse(code = 500, message = "Unexpected error occurred while fetching metric's tags.",
                response = ApiError.class) })
    public void getMetricTags(
            @Suspended final AsyncResponse asyncResponse,
            @PathParam("id") String id
    ) {
        metricsService.getMetricTags(new MetricId<>(tenantId, AVAILABILITY, id))
                .map(ApiUtils::mapToResponse)
                .subscribe(asyncResponse::resume, t -> asyncResponse.resume(ApiUtils.serverError(t)));
    }

    @PUT
    @Path("/{id}/tags")
    @ApiOperation(value = "Update tags associated with the metric definition.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Metric's tags were successfully updated."),
            @ApiResponse(code = 500, message = "Unexpected error occurred while updating metric's tags.",
                response = ApiError.class) })
    public void updateMetricTags(
            @Suspended final AsyncResponse asyncResponse,
            @PathParam("id") String id,
            @ApiParam(required = true) Map<String, String> tags
    ) {
        Metric<AvailabilityType> metric = new Metric<>(new MetricId<>(tenantId, AVAILABILITY, id));
        metricsService.addTags(metric, tags).subscribe(new ResultSetObserver(asyncResponse));
    }

    @DELETE
    @Path("/{id}/tags/{tags}")
    @ApiOperation(value = "Delete tags associated with the metric definition.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Metric's tags were successfully deleted."),
            @ApiResponse(code = 400, message = "Invalid tags", response = ApiError.class),
            @ApiResponse(code = 500, message = "Unexpected error occurred while trying to delete metric's tags.",
                response = ApiError.class) })
    public void deleteMetricTags(
            @Suspended final AsyncResponse asyncResponse,
            @PathParam("id") String id,
            @ApiParam("Tag list") @PathParam("tags") Tags tags
    ) {
        Metric<AvailabilityType> metric = new Metric<>(new MetricId<>(tenantId, AVAILABILITY, id));
        metricsService.deleteTags(metric, tags.getTags()).subscribe(new ResultSetObserver(asyncResponse));
    }

    @POST
    @Path("/{id}/raw")
    @ApiOperation(value = "Add data for a single availability metric.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Adding data succeeded."),
            @ApiResponse(code = 400, message = "Missing or invalid payload", response = ApiError.class),
            @ApiResponse(code = 500, message = "Unexpected error happened while storing the data",
                    response = ApiError.class)
    })
    public void addAvailabilityForMetric(
            @Suspended final AsyncResponse asyncResponse, @PathParam("id") String id,
            @ApiParam(value = "List of availability datapoints", required = true) List<DataPoint<AvailabilityType>> data
    ) {
        Observable<Metric<AvailabilityType>> metrics = Functions.dataPointToObservable(tenantId, id, data,
                AVAILABILITY);
        Observable<Void> observable = metricsService.addDataPoints(AVAILABILITY, metrics);
        observable.subscribe(new ResultSetObserver(asyncResponse));
    }

    @Deprecated
    @POST
    @Path("/{id}/data")
    @ApiOperation(value = "Deprecated. Please use /raw endpoint.")
    public void deprecatedAddAvailabilityForMetric(
            @Suspended final AsyncResponse asyncResponse, @PathParam("id") String id,
            @ApiParam(value = "List of availability datapoints", required = true)
                    List<DataPoint<AvailabilityType>> data) {
        addAvailabilityForMetric(asyncResponse, id, data);
    }

    @POST
    @Path("/raw")
    @ApiOperation(value = "Add metric data for multiple availability metrics in a single call.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Adding data succeeded."),
            @ApiResponse(code = 400, message = "Missing or invalid payload", response = ApiError.class),
            @ApiResponse(code = 500, message = "Unexpected error happened while storing the data",
                    response = ApiError.class)
    })
    public void addAvailabilityData(
            @Suspended final AsyncResponse asyncResponse,
            @ApiParam(value = "List of availability metrics", required = true)
            @JsonDeserialize()
            List<Metric<AvailabilityType>> availabilities
    ) {
        Observable<Metric<AvailabilityType>> metrics = Functions.metricToObservable(tenantId, availabilities,
                AVAILABILITY);
        Observable<Void> observable = metricsService.addDataPoints(AVAILABILITY, metrics);
        observable.subscribe(new ResultSetObserver(asyncResponse));
    }

    @Deprecated
    @POST
    @Path("/data")
    @ApiOperation(value = "Deprecated. Please use /raw endpoint.")
    public void deprecatedAddAvailabilityData(
            @Suspended final AsyncResponse asyncResponse,
            @ApiParam(value = "List of availability metrics", required = true)
            @JsonDeserialize() List<Metric<AvailabilityType>> availabilities
    ) {
        addAvailabilityData(asyncResponse, availabilities);
    }

    @Deprecated
    @GET
    @Path("/{id}/data")
    @ApiOperation(value = "Deprecated. Please use /raw or /stats endpoints.", response = DataPoint.class,
            responseContainer = "List")
    public void findAvailabilityData(
            @Suspended AsyncResponse asyncResponse,
            @PathParam("id") String id,
            @ApiParam(value = "Defaults to now - 8 hours") @QueryParam("start") Long start,
            @ApiParam(value = "Defaults to now") @QueryParam("end") Long end,
            @ApiParam(value = "Total number of buckets") @QueryParam("buckets") Integer bucketsCount,
            @ApiParam(value = "Bucket duration") @QueryParam("bucketDuration") Duration bucketDuration,
            @ApiParam(value = "Set to true to return only distinct, contiguous values")
                @QueryParam("distinct") @DefaultValue("false") Boolean distinct,
            @ApiParam(value = "Limit the number of data points returned") @QueryParam("limit") Integer limit,
            @ApiParam(value = "Data point sort order, based on timestamp") @QueryParam("order") Order order
    ) {
        if ((bucketsCount != null || bucketDuration != null) &&
                (limit != null || order != null)) {
            asyncResponse.resume(badRequest(new ApiError("Limit and order cannot be used with bucketed results")));
            return;
        }

        TimeRange timeRange = new TimeRange(start, end);
        if (!timeRange.isValid()) {
            asyncResponse.resume(badRequest(new ApiError(timeRange.getProblem())));
            return;
        }
        BucketConfig bucketConfig = new BucketConfig(bucketsCount, bucketDuration, timeRange);
        if (!bucketConfig.isValid()) {
            asyncResponse.resume(badRequest(new ApiError(bucketConfig.getProblem())));
            return;
        }

        MetricId<AvailabilityType> metricId = new MetricId<>(tenantId, AVAILABILITY, id);
        Buckets buckets = bucketConfig.getBuckets();
        if (buckets == null) {
            if (limit != null) {
                if (order == null) {
                    if (start == null && end != null) {
                        order = Order.DESC;
                    } else if (start != null && end == null) {
                        order = Order.ASC;
                    } else {
                        order = Order.DESC;
                    }
                }
            } else {
                limit = 0;
            }

            if (order == null) {
                order = Order.DESC;
            }

            metricsService
                    .findAvailabilityData(metricId, timeRange.getStart(), timeRange.getEnd(), distinct, limit, order)
                    .toList()
                    .map(ApiUtils::collectionToResponse)
                    .subscribe(asyncResponse::resume, t -> asyncResponse.resume(serverError(t)));
        } else {
            metricsService.findAvailabilityStats(metricId, timeRange.getStart(), timeRange.getEnd(), buckets)
                .map(ApiUtils::collectionToResponse)
                    .subscribe(asyncResponse::resume, t -> asyncResponse.resume(serverError(t)));
        }
    }

    @GET
    @Path("/{id}/raw")
    @ApiOperation(value = "Retrieve availability data.", response = DataPoint.class, responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully fetched availability data."),
            @ApiResponse(code = 204, message = "No availability data was found."),
            @ApiResponse(code = 400, message = "buckets or bucketDuration parameter is invalid, or both are used.",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Unexpected error occurred while fetching availability data.",
                    response = ApiError.class)
    })
    public void findRawAvailabilityData(
            @Suspended AsyncResponse asyncResponse,
            @PathParam("id") String id,
            @ApiParam(value = "Defaults to now - 8 hours") @QueryParam("start") Long start,
            @ApiParam(value = "Defaults to now") @QueryParam("end") Long end,
            @ApiParam(value = "Set to true to return only distinct, contiguous values")
                @QueryParam("distinct") @DefaultValue("false") Boolean distinct,
            @ApiParam(value = "Limit the number of data points returned") @QueryParam("limit") Integer limit,
            @ApiParam(value = "Data point sort order, based on timestamp") @QueryParam("order") Order order
    ) {

        TimeRange timeRange = new TimeRange(start, end);
        if (!timeRange.isValid()) {
            asyncResponse.resume(badRequest(new ApiError(timeRange.getProblem())));
            return;
        }

        MetricId<AvailabilityType> metricId = new MetricId<>(tenantId, AVAILABILITY, id);
        if (limit != null) {
            if (order == null) {
                if (start == null && end != null) {
                    order = Order.DESC;
                } else if (start != null && end == null) {
                    order = Order.ASC;
                } else {
                    order = Order.DESC;
                }
            }
        } else {
            limit = 0;
        }

        if (order == null) {
            order = Order.DESC;
        }

        metricsService
                .findAvailabilityData(metricId, timeRange.getStart(), timeRange.getEnd(), distinct, limit, order)
                .toList()
                .map(ApiUtils::collectionToResponse)
                .subscribe(asyncResponse::resume, t -> asyncResponse.resume(serverError(t)));
    }

    @GET
    @Path("/{id}/stats")
    @ApiOperation(value = "Retrieve availability data.", notes = "Based on buckets or bucketDuration query parameter" +
            ", the time range between start and end will be divided in buckets of equal duration, and " +
            "availability statistics will be computed for each bucket.", response = DataPoint.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully fetched availability data."),
            @ApiResponse(code = 204, message = "No availability data was found."),
            @ApiResponse(code = 400, message = "buckets or bucketDuration parameter is invalid, or both are used.",
                    response = ApiError.class),
            @ApiResponse(code = 500, message = "Unexpected error occurred while fetching availability data.",
                    response = ApiError.class)
    })
    public void findStatsAvailabilityData(
            @Suspended AsyncResponse asyncResponse,
            @PathParam("id") String id,
            @ApiParam(value = "Defaults to now - 8 hours") @QueryParam("start") Long start,
            @ApiParam(value = "Defaults to now") @QueryParam("end") Long end,
            @ApiParam(value = "Total number of buckets") @QueryParam("buckets") Integer bucketsCount,
            @ApiParam(value = "Bucket duration") @QueryParam("bucketDuration") Duration bucketDuration) {

        TimeRange timeRange = new TimeRange(start, end);
        if (!timeRange.isValid()) {
            asyncResponse.resume(badRequest(new ApiError(timeRange.getProblem())));
            return;
        }

        BucketConfig bucketConfig = new BucketConfig(bucketsCount, bucketDuration, timeRange);
        if (!bucketConfig.isValid()) {
            asyncResponse.resume(badRequest(new ApiError(bucketConfig.getProblem())));
            return;
        }

        if (bucketConfig.isEmpty()) {
            asyncResponse.resume(badRequest(new ApiError(
                    "Either the buckets or bucketDuration parameter must be used")));
            return;
        }

        MetricId<AvailabilityType> metricId = new MetricId<>(tenantId, AVAILABILITY, id);
        Buckets buckets = bucketConfig.getBuckets();

        metricsService.findAvailabilityStats(metricId, timeRange.getStart(), timeRange.getEnd(), buckets)
                .map(ApiUtils::collectionToResponse)
                .subscribe(asyncResponse::resume, t -> asyncResponse.resume(serverError(t)));
    }
}
