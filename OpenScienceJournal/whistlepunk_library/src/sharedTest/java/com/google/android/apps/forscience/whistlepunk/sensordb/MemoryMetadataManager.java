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

package com.google.android.apps.forscience.whistlepunk.sensordb;

import android.support.annotation.NonNull;

import com.google.android.apps.forscience.whistlepunk.data.GoosciSensorLayout;
import com.google.android.apps.forscience.whistlepunk.metadata.BleSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.Experiment;
import com.google.android.apps.forscience.whistlepunk.metadata.ExternalSensorSpec;
import com.google.android.apps.forscience.whistlepunk.metadata.Label;
import com.google.android.apps.forscience.whistlepunk.metadata.MetaDataManager;
import com.google.android.apps.forscience.whistlepunk.metadata.Project;
import com.google.android.apps.forscience.whistlepunk.metadata.Run;
import com.google.android.apps.forscience.whistlepunk.metadata.RunStats;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemoryMetadataManager implements MetaDataManager {
    private Project mLastUsedProject = null;
    private ListMultimap<String, Experiment> mExperimentsPerProject = ArrayListMultimap.create();

    @Override
    public Project getProjectById(String projectId) {
        return null;
    }

    @Override
    public List<Project> getProjects(int maxNumber, boolean archived) {
        return null;
    }

    @Override
    public Project newProject() {
        Project project = new Project(System.currentTimeMillis());
        mLastUsedProject = project;
        return project;
    }

    @Override
    public void updateProject(Project project) {
    }

    @Override
    public void deleteProject(Project project) {

    }

    @Override
    public Experiment getExperimentById(String experimentId) {
        return null;
    }

    @Override
    public Experiment newExperiment(Project project) {
        long timestamp = System.currentTimeMillis();
        String experimentId = String.valueOf(timestamp);
        return newExperiment(project, timestamp, experimentId);
    }

    @NonNull
    public Experiment newExperiment(Project project, long timestamp, String experimentId) {
        Experiment experiment = new Experiment(timestamp);
        experiment.setExperimentId(experimentId);
        experiment.setProjectId(project.getProjectId());
        experiment.setTimestamp(timestamp);
        mExperimentsPerProject.get(project.getProjectId()).add(0, experiment);
        return experiment;
    }

    @Override
    public void deleteExperiment(Experiment experiment) {

    }

    @Override
    public void updateExperiment(Experiment experiment) {

    }

    @Override
    public List<Experiment> getExperimentsForProject(Project project, boolean includeArchived) {
        return Lists.newArrayList(mExperimentsPerProject.get(project.getProjectId()));
    }

    private ListMultimap<String, Label> mLabels = LinkedListMultimap.create();

    @Override
    public void addLabel(Experiment experiment, Label label) {
        mLabels.put(experiment.getExperimentId(), label);
    }

    @Override
    public void addLabel(String experimentId, Label label) {

    }

    @Override
    public List<Label> getLabelsForExperiment(Experiment experiment) {
        return mLabels.get(experiment.getExperimentId());
    }

    @Override
    public List<Label> getLabelsWithStartId(String startLabelId) {
        final ArrayList<Label> labels = new ArrayList<>();
        for (Label label : mLabels.values()) {
            if (label.getRunId().equals(startLabelId)) {
                labels.add(label);
            }
        }
        return labels;
    }

    Table<String, String, RunStats> mStats = HashBasedTable.create();

    @Override
    public void setStats(String startLabelId, String sensorId, RunStats stats) {
        mStats.put(startLabelId, sensorId, stats);
    }

    @Override
    public RunStats getStats(String startLabelId, String sensorId) {
        return mStats.get(startLabelId, sensorId);
    }

    private Map<String, Run> mRuns = new HashMap<>();
    private ListMultimap<String, String> mExperimentIdsToRunIds = LinkedListMultimap.create();

    @Override
    public List<String> getExperimentRunIds(String experimentId, boolean includeArchived) {
        return mExperimentIdsToRunIds.get(experimentId);
    }

    @Override
    public void editLabel(Label updatedLabel) {

    }

    @Override
    public void deleteLabel(Label label) {

    }

    private Map<String, ExternalSensorSpec> mExternalSensors = new HashMap<>();

    @Override
    public Map<String, ExternalSensorSpec> getExternalSensors() {
        return mExternalSensors;
    }

    @Override
    public ExternalSensorSpec getExternalSensorById(String id) {
        return mExternalSensors.get(id);
    }

    @Override
    public void removeExternalSensor(String databaseTag) {
        mExternalSensors.remove(databaseTag);
    }


    @Override
    public String addOrGetExternalSensor(ExternalSensorSpec sensor) {
        for (Map.Entry<String, ExternalSensorSpec> entry : mExternalSensors.entrySet()) {
            if (Arrays.equals(entry.getValue().getConfig(), sensor.getConfig())) {
                return entry.getKey();
            }
        }
        int suffix = 0;
        while (mExternalSensors.containsKey(ExternalSensorSpec.getSensorId(sensor, suffix))) {
            suffix++;
        }
        String newId = ExternalSensorSpec.getSensorId(sensor, suffix);
        mExternalSensors.put(newId, cloneSensor(sensor));
        return newId;
    }

    private BleSensorSpec cloneSensor(ExternalSensorSpec sensor) {
        BleSensorSpec newSensor = new BleSensorSpec(sensor.getAddress(), sensor.getName());
        newSensor.loadFromConfig(sensor.getConfig());
        return newSensor;
    }

    private Multimap<String, String> mExperimentToSensors = HashMultimap.create();

    @Override
    public void addSensorToExperiment(String databaseTag, String experimentId) {
        mExperimentToSensors.put(experimentId, databaseTag);
    }

    @Override
    public void removeSensorFromExperiment(String databaseTag, String experimentId) {
        mExperimentToSensors.remove(experimentId, databaseTag);
    }

    @Override
    public Map<String, ExternalSensorSpec> getExperimentExternalSensors(String experimentId) {
        Map<String, ExternalSensorSpec> specs = new HashMap<>();
        for (String id : mExperimentToSensors.get(experimentId)) {
            specs.put(id, mExternalSensors.get(id));
        }
        return specs;
    }

    @Override
    public Experiment getLastUsedExperiment() {
        return null;
    }

    @Override
    public Project getLastUsedProject() {
        return mLastUsedProject;
    }

    @Override
    public void updateLastUsedProject(Project project) {

    }

    @Override
    public void updateLastUsedExperiment(Experiment experiment) {
        String projectId = experiment.getProjectId();
        Collection<Experiment> experiments = Lists.newArrayList(
                mExperimentsPerProject.get(projectId));
        mExperimentsPerProject.removeAll(projectId);
        mExperimentsPerProject.put(projectId, experiment);
        for (Experiment e : experiments) {
            if (e.getExperimentId() != experiment.getExperimentId()) {
                mExperimentsPerProject.put(projectId, e);
            }
        }
    }

    @Override
    public Run newRun(Experiment experiment, String runId,
            List<GoosciSensorLayout.SensorLayout> sensorLayouts) {
        final Run run = new Run(runId, mRuns.size(), sensorLayouts, true);
        mRuns.put(run.getId(), run);
        mExperimentIdsToRunIds.put(experiment.getExperimentId(), run.getId());
        return run;
    }

    @Override
    public Run getRun(String runId) {
        return mRuns.get(runId);
    }


    Map<String, List<GoosciSensorLayout.SensorLayout>> mLayouts = new HashMap<>();

    @Override
    public void setExperimentSensorLayout(String experimentId,
            List<GoosciSensorLayout.SensorLayout> sensorLayouts) {
        mLayouts.put(experimentId, sensorLayouts);
    }

    @Override
    public List<GoosciSensorLayout.SensorLayout> getExperimentSensorLayout(String experimentId) {
        List<GoosciSensorLayout.SensorLayout> layouts = mLayouts.get(experimentId);
        if (layouts == null) {
            return Collections.emptyList();
        } else {
            return layouts;
        }
    }

    @Override
    public void close() {

    }

    @Override
    public void updateRun(Run run) {

    }

    @Override
    public void deleteRun(String runId) {

    }
}
