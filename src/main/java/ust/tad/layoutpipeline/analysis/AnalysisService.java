package ust.tad.layoutpipeline.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import ust.tad.layoutpipeline.analysistask.AnalysisTaskResponseSender;
import ust.tad.layoutpipeline.analysistask.Location;
import ust.tad.layoutpipeline.models.ModelsService;
import ust.tad.layoutpipeline.models.tadm.InvalidPropertyValueException;
import ust.tad.layoutpipeline.models.tadm.InvalidRelationException;
import ust.tad.layoutpipeline.models.tadm.TechnologyAgnosticDeploymentModel;
import ust.tad.layoutpipeline.models.tsdm.InvalidAnnotationException;
import ust.tad.layoutpipeline.models.tsdm.InvalidNumberOfContentException;
import ust.tad.layoutpipeline.models.tsdm.InvalidNumberOfLinesException;
import ust.tad.layoutpipeline.models.tsdm.TechnologySpecificDeploymentModel;

@Service
public class AnalysisService {

    @Autowired
    ModelsService modelsService;

    @Autowired
    AnalysisTaskResponseSender analysisTaskResponseSender;

    private TechnologySpecificDeploymentModel tsdm;

    private TechnologyAgnosticDeploymentModel tadm;

    private Set<Integer> newEmbeddedDeploymentModelIndexes = new HashSet<>();

    public void startAnalysis(UUID taskId, UUID transformationProcessId, List<String> commands, List<Location> locations) {
        this.tsdm = modelsService.getTechnologySpecificDeploymentModel(transformationProcessId);
        this.tadm = modelsService.getTechnologyAgnosticDeploymentModel(transformationProcessId);

        try {
            runAnalysis(locations);
        } catch (InvalidNumberOfContentException | URISyntaxException | IOException |
                 InvalidNumberOfLinesException | InvalidAnnotationException e) {
            e.printStackTrace();
            analysisTaskResponseSender.sendFailureResponse(taskId,
                    e.getClass() + ": " + e.getMessage());
            return;
        }

        updateDeploymentModels(this.tsdm, this.tadm);

        if (!newEmbeddedDeploymentModelIndexes.isEmpty()) {
            for (int index : newEmbeddedDeploymentModelIndexes) {
                analysisTaskResponseSender.sendEmbeddedDeploymentModelAnalysisRequestFromModel(
                        this.tsdm.getEmbeddedDeploymentModels().get(index), taskId);
            }
        }

        analysisTaskResponseSender.sendSuccessResponse(taskId);
    }

    private void updateDeploymentModels(TechnologySpecificDeploymentModel tsdm, TechnologyAgnosticDeploymentModel tadm) {
        modelsService.updateTechnologySpecificDeploymentModel(tsdm);
        modelsService.updateTechnologyAgnosticDeploymentModel(tadm);
    }

    private void runAnalysis(List<Location> locations) throws URISyntaxException, IOException, InvalidNumberOfLinesException, InvalidAnnotationException, InvalidNumberOfContentException {}

    private void analyzeFile(URL url) throws IOException/*, InvalidNumberOfLinesException, InvalidAnnotationException*/{}

    private void addEmbeddedDeploymentModel(String technology, String lineContent, URL currentDirectory) throws MalformedURLException/*, InvalidAnnotationException, InvalidNumberOfLinesException*/ {}
}
