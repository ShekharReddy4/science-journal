/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.android.apps.forscience.whistlepunk;

import android.os.SystemClock;
import android.support.annotation.NonNull;

import com.google.android.apps.forscience.javalib.FailureListener;
import com.google.android.apps.forscience.javalib.FallibleConsumer;
import com.google.android.apps.forscience.javalib.MaybeConsumers;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReading;
import com.google.android.apps.forscience.whistlepunk.sensordb.ScalarReadingList;
import com.google.android.apps.forscience.whistlepunk.sensordb.TimeRange;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;

import java.util.List;

// TODO(saff): port tests from Weather
public class GraphPopulator {
    // How many datapoints do we grab from the database at one time?
    private static final int MAX_DATAPOINTS_PER_SENSOR_LOAD = 100;

    private Range<Long> mRequestedTimes = null;
    private ObservationDisplay mObservationDisplay;
    private boolean mRequestInFlight = false;
    private final long mRequestId;

    public GraphPopulator(ObservationDisplay observationDisplay) {
        mObservationDisplay = observationDisplay;
        mRequestId = SystemClock.uptimeMillis();
    }

    /**
     * GraphStatus for a graph that is not changing its x axis.
     */
    @NonNull
    public static GraphStatus constantGraphStatus(final long firstTimestamp,
            final long lastTimestamp) {
        return new GraphStatus() {
                @Override
                public long getMinTime() {
                    return firstTimestamp;
                }

                @Override
                public long getMaxTime() {
                    return lastTimestamp;
                }

                @Override
                public boolean graphIsStillValid() {
                    // TODO(saff): should return false once the activity is disposed
                    return true;
                }
            };
    }

    /**
     * If the graphStatus shows that there are still values that need to be fetched to fill the
     * currently-displayed graph, this method will begin fetching them.
     * <p/>
     * Call only on the UI thread.
     */
    public long requestObservations(final GraphStatus graphStatus,
            final DataController dataController, final FailureListener failureListener,
            final int resolutionTier, final String sensorId) {
        if (mRequestInFlight) {
            return mRequestId;
        }
        final TimeRange r = getRequestRange(graphStatus);
        if (r == null) {
            mObservationDisplay.onFinish(mRequestId);
        } else {
            mRequestInFlight = true;
            dataController.getScalarReadings(sensorId, resolutionTier, r,
                    MAX_DATAPOINTS_PER_SENSOR_LOAD, MaybeConsumers.chainFailure(failureListener,
                            new FallibleConsumer<ScalarReadingList>() {
                                @Override
                                public void take(ScalarReadingList observations) {
                                    mRequestInFlight = false;
                                    if (graphStatus.graphIsStillValid()) {
                                        final Range<Long> received =
                                                addObservationsToDisplay(observations);
                                        if (received != null) {
                                            mObservationDisplay.addRange(observations, mRequestId);
                                        }
                                        addToRequestedTimes(getEffectiveAddedRange(r, received));
                                        requestObservations(graphStatus, dataController,
                                                failureListener, resolutionTier, sensorId);
                                    }
                                }

                                public void addToRequestedTimes(Range<Long> effectiveAdded) {
                                    mRequestedTimes = Ranges.span(mRequestedTimes, effectiveAdded);
                                }

                                public Range<Long> addObservationsToDisplay(
                                        ScalarReadingList observations) {
                                    List<ScalarReading> points = ScalarReading.slurp(observations);
                                    Range<Long> range = null;
                                    for (ScalarReading point : points) {
                                        range = Ranges.span(range, Range.singleton(
                                                point.getCollectedTimeMillis()));
                                    }
                                    return range;
                                }
                            })
            );
        }
        return mRequestId;
    }

    private TimeRange getRequestRange(GraphStatus graphStatus) {
        final long minTime = graphStatus.getMinTime();
        final long maxTime = graphStatus.getMaxTime();
        // TODO(saff): push more of this computation to be testable in NextRequestType
        return computeRequestRange(NextRequestType.compute(mRequestedTimes, minTime, maxTime),
                minTime, maxTime);
    }

    private TimeRange computeRequestRange(NextRequestType type, long minTime, long maxTime) {
        switch (type) {
            case NONE:
                return null;
            case FIRST:
                return TimeRange.oldest(Range.closed(minTime, maxTime));
            case NEXT_LOWER:
                return TimeRange.oldest(Range.closedOpen(minTime, mRequestedTimes.lowerEndpoint()));
            case NEXT_HIGHER:
                return TimeRange.oldest(Range.openClosed(mRequestedTimes.upperEndpoint(), maxTime));
            default:
                throw new IllegalStateException("Should never happen");
        }
    }

    private Range<Long> getEffectiveAddedRange(TimeRange requested, Range<Long> returned) {
        if (returned == null) {
            return requested.getTimes().canonical(DiscreteDomain.longs());
        }

        switch (requested.getOrder()) {
            case NEWEST_FIRST:
                return Range.closed(returned.lowerEndpoint(), requested.getTimes().upperEndpoint());
            case OLDEST_FIRST:
                return Range.closed(requested.getTimes().lowerEndpoint(), returned.upperEndpoint());
            default:
                throw new IllegalArgumentException(
                        "Unexpected value for enum: " + requested.getOrder());
        }
    }

    public interface GraphStatus {
        long getMinTime();

        long getMaxTime();

        boolean graphIsStillValid();
    }

    public interface ObservationDisplay {
        void addRange(ScalarReadingList observations, long requestId);

        void onFinish(long requestId);
    }
}