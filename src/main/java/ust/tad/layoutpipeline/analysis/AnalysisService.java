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

@Service
public class AnalysisService {
    public void startAnalysis(UUID taskId, UUID transformationProcessId, List<String> commands, List<Location> locations) {}

    //private void updateDeploymentModels(TechnologySpecificDeploymentModel tsdm, TechnologyAgnosticDeploymentModel tadm){}

    private void runAnalysis(List<String> commands, List<Location> locations) throws URISyntaxException, IOException/*, InvalidNumberOfLinesException, InvalidAnnotationException, InvalidNumberOfContentException*/ {}

    private void analyzeFile(URL url) throws IOException/*, InvalidNumberOfLinesException, InvalidAnnotationException*/{}

    private void addEmbeddedDeploymentModel(String technology, String lineContent, URL currentDirectory) throws MalformedURLException/*, InvalidAnnotationException, InvalidNumberOfLinesException*/ {}
}
