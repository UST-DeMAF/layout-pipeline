package ust.tad.layoutpipeline.analysis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import ust.tad.layoutpipeline.analysistask.AnalysisTaskResponseSender;
import ust.tad.layoutpipeline.analysistask.Location;
import ust.tad.layoutpipeline.models.ModelsService;
import ust.tad.layoutpipeline.models.tadm.*;
import ust.tad.layoutpipeline.models.tsdm.InvalidAnnotationException;
import ust.tad.layoutpipeline.registration.PluginRegistrationRunner;

@Service
public class AnalysisService {

    @Autowired
    ModelsService modelsService;

    @Autowired
    AnalysisTaskResponseSender analysisTaskResponseSender;

    @Autowired
    LayoutService layoutService;

    private static final Logger LOG = LoggerFactory.getLogger(PluginRegistrationRunner.class);

    private static final Set<String> supportedFileExtensions = Set.of("yaml", "yml");

    private BufferedReader reader;
    private String line;
    private List<String> cachedLines = new ArrayList<>();

    private TechnologyAgnosticDeploymentModel tadm;

    private UUID transformationProcessId;

    private List<Property> properties = new ArrayList<>();
    private List<Component> components = new ArrayList<>();
    private List<Relation> relations = new ArrayList<>();
    private List<ComponentType> componentTypes = new ArrayList<>();
    private List<RelationType> relationTypes = new ArrayList<>();

    public void startAnalysis(UUID taskId, UUID transformationProcessId, List<String> commands, List<Location> locations) {
        this.tadm = modelsService.getTechnologyAgnosticDeploymentModel(transformationProcessId);

        if (!commands.isEmpty() && !locations.isEmpty()) {
            this.transformationProcessId = transformationProcessId;
            try {
                runAnalysis(locations);
            } catch (IOException | InvalidAnnotationException | InvalidPropertyValueException |
                     InvalidRelationException e) {
                e.printStackTrace();
                analysisTaskResponseSender.sendFailureResponse(taskId,
                        e.getClass() + ": " + e.getMessage());
                return;
            }
            //modelsService.updateTechnologyAgnosticDeploymentModel(this.tadm);
        }
        layoutService.generateLayout(this.tadm);
        analysisTaskResponseSender.sendSuccessResponse(taskId);
    }

    private void runAnalysis(List<Location> locations) throws IOException, InvalidAnnotationException, InvalidPropertyValueException, InvalidRelationException {
        for (Location location : locations) {
            String fileExtension = StringUtils.getFilenameExtension(location.getUrl().toString());
            if (supportedFileExtensions.contains(fileExtension)) {
                parseFile(location.getUrl());
            }
        }
        this.tadm = new TechnologyAgnosticDeploymentModel(transformationProcessId, properties, components, relations, componentTypes, relationTypes);
    }

    private boolean isDouble(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isInteger(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private List<Artifact> readArtifacts() throws IOException {
        List<Artifact> artifacts = new ArrayList<>();
        if (line.startsWith("artifacts") && !line.contains("[]")) {
            line = reader.readLine().trim();
            while (line.startsWith("-")) {
                cachedLines.add(reader.readLine().trim());
                cachedLines.add(reader.readLine().trim());
                if (cachedLines.get(0).startsWith("name") && cachedLines.get(1).startsWith("fileURI")) {
                    Artifact artifact = new Artifact();
                    String[] split = line.split(":", 2);
                    if (!split[1].isEmpty()) {
                        artifact.setType(split[1].trim());
                    } else {
                        artifact.setType(split[0].replaceFirst("- ", ""));
                    }
                    artifact.setName(cachedLines.get(0).split(":")[1].trim().replaceAll("\"", ""));
                    String fileURI = cachedLines.get(1).split(":")[1].trim().replaceAll("\"", "");
                    if (!fileURI.equals("-")) {
                        artifact.setFileUri(URI.create(fileURI));
                    }
                    artifact.setConfidence(Confidence.SUSPECTED);
                    artifacts.add(artifact);
                    cachedLines.clear();
                } else {
                    return artifacts;
                }
                line = reader.readLine().trim();
            }
        } else {
            line = reader.readLine().trim();
        }
        return artifacts;
    }

    private List<Operation> readOperations() throws IOException {
        List<Operation> operations = new ArrayList<>();
        if (line.startsWith("operations") && !line.contains("[]")) {
            //TODO: Needs to be tested.
            line = reader.readLine().trim();
            while (line.startsWith("-")) {
                Operation operation = new Operation();

                String[] split = line.split(":", 2);
                if (!split[1].isEmpty()) {
                    operation.setName(split[1].replaceAll("\\s|\"", ""));
                } else {

                    operation.setName(split[0].replaceFirst("- ", ""));
                }

                line = reader.readLine().trim();
                if (line.startsWith("artifacts")) {
                    operation.setArtifacts(readArtifacts());
                }
                operation.setConfidence(Confidence.CONFIRMED);
                operations.add(operation);
            }
        } else {
            line = reader.readLine().trim();
        }
        return operations;
    }

    void extractProperty(Property property) throws InvalidPropertyValueException {
        if (line.startsWith("type")) {
            switch (line.split(":", 2)[1].replaceAll("\\s|\"", "")) {
                case "BOOLEAN":
                    property.setType(PropertyType.BOOLEAN);
                    property.setValue(false);
                    break;
                case "DOUBLE":
                    property.setType(PropertyType.DOUBLE);
                    property.setValue(0.0);
                    break;
                case "INTEGER":
                    property.setType(PropertyType.INTEGER);
                    property.setValue(0);
                    break;
                default:
                    property.setType(PropertyType.STRING);
                    property.setValue("");
                    break;
            }
        } else if (line.startsWith("default_value") || line.startsWith("value")) {
            switch (property.getType()) {
                case BOOLEAN:
                    property.setValue(line.split(":", 2)[1].replaceAll("\\s|\"", "").equalsIgnoreCase("true"));
                    break;
                case DOUBLE:
                    property.setValue(Double.valueOf(line.split(":", 2)[1].replaceAll("\\s|\"", "")));
                    break;
                case INTEGER:
                    property.setValue(Integer.valueOf(line.split(":", 2)[1].replaceAll("\\s|\"", "")));
                    break;
                default:
                    property.setValue(line.split(":", 2)[1].replaceAll("\\s|\"", ""));
                    break;
            }
        } else if (line.startsWith("required")) {
            property.setRequired(line.split(":", 2)[1].equalsIgnoreCase("true"));
        }
    }

    private void readGlobalProperties() throws IOException, InvalidPropertyValueException {
        line = reader.readLine().trim();
        while (reader.ready() && !line.startsWith("properties")) {
            line = reader.readLine().trim();
        }
        properties = readProperties();
    }

    private List<Property> readProperties() throws IOException, InvalidPropertyValueException {
        List<Property> properties = new ArrayList<>();
        if (line.startsWith("properties") && !line.contains("[]")) {
            line = reader.readLine().trim();
            while (line.startsWith("-")) {
                Property property = new Property();
                String[] split = line.split(":", 2);
                if (!split[1].isEmpty()) {
                    if (split[0].contains("key")) {
                        property.setKey(split[1].trim().replaceAll("\"", ""));
                        line = reader.readLine().trim();
                        while (reader.ready() && !line.startsWith("-") && !line.startsWith("operations")) {
                            extractProperty(property);
                            line = reader.readLine().trim();
                        }
                    } else {
                        property.setKey(split[0].replaceFirst("- ", ""));
                        String value = split[1].trim().replaceAll("\"", "");

                        boolean typeDouble = isDouble(value);
                        boolean typeInteger = isInteger(value);

                        if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("true")) {
                            property.setValue(value.equalsIgnoreCase("true"));
                            property.setType(PropertyType.BOOLEAN);
                        } else if (typeDouble && !typeInteger) {
                            property.setValue(Double.valueOf(value));
                            property.setType(PropertyType.DOUBLE);
                        } else if (typeInteger) {
                            property.setValue(Integer.valueOf(value));
                            property.setType(PropertyType.INTEGER);
                        } else {
                            property.setValue(value);
                            property.setType(PropertyType.STRING);
                        }

                        property.setRequired(false);
                        property.setConfidence(Confidence.SUSPECTED);
                        line = reader.readLine().trim();
                    }
                } else {
                    property.setKey(split[0].replaceAll("\\s|-", ""));
                    line = reader.readLine().trim();
                    while (reader.ready() && !line.startsWith("-") && !line.startsWith("operations")) {
                        extractProperty(property);
                        line = reader.readLine().trim();
                    }
                    property.setConfidence(Confidence.CONFIRMED);
                }
                properties.add(property);
            }
        } else {
            line = reader.readLine().trim();
        }
        return properties;
    }

    private void readComponentTypes() throws IOException, InvalidPropertyValueException {
        line = reader.readLine().trim();
        while (reader.ready() && !line.startsWith("component_types")) {
            line = reader.readLine().trim();
        }
        line = reader.readLine().trim();
        while (line.startsWith("-")) {
            ComponentType componentType = new ComponentType();

            String[] split = line.split(":", 2);
            if (!split[1].isEmpty()) {
                componentType.setName(split[1].replaceAll("\\s|\"", ""));
            } else {
                componentType.setName(split[0].replaceFirst("- ", ""));
            }

            line = reader.readLine().trim();
            while (!line.startsWith("-") && !line.startsWith("relation_types")) {
                if (line.startsWith("extends")) {
                    String value = line.split(":", 2)[1].replaceAll("\\s|\"", "");
                    if (!value.equals("-")) {
                        for (ComponentType parentType : componentTypes) {
                            if (parentType.getName().equals(value)) {
                                componentType.setParentType(parentType);
                            }
                        }
                    }
                    line = reader.readLine().trim();
                } else if (line.startsWith("description")) {
                    String value = line.split(":", 2)[1].trim().replaceAll("\"", "");
                    componentType.setDescription(value);
                    line = reader.readLine().trim();
                } else if (line.startsWith("properties")) {
                    componentType.setProperties(readProperties());
                } else if (line.startsWith("operations")) {
                    componentType.setOperations(readOperations());
                }
            }
            componentTypes.add(componentType);
        }
    }

    private void readRelationTypes() throws IOException, InvalidPropertyValueException {
        while (reader.ready() && !line.startsWith("relation_types")) {
            line = reader.readLine().trim();
        }
        line = reader.readLine().trim();
        while (line.startsWith("-")) {
            RelationType relationType = new RelationType();
            String name = line.split(":", 2)[0].replaceFirst("- ", "");
            relationType.setName(name);

            line = reader.readLine().trim();
            while (reader.ready() && !line.startsWith("-")) {
                if (line.startsWith("extends")) {
                    String value = line.split(":", 2)[1].replaceAll("\\s|\"", "");
                    if (!value.equals("-")) {
                        for (RelationType parentType : relationTypes) {
                            if (parentType.getName().equals(value)) {
                                relationType.setParentType(parentType);
                            }
                        }
                    }
                    line = reader.readLine().trim();
                } else if (line.startsWith("description")) {
                    String value = line.split(":", 2)[1].replaceAll("\\s|\"", "");
                    relationType.setDescription(value);
                    line = reader.readLine().trim();
                } else if (line.startsWith("properties")) {
                    relationType.setProperties(readProperties());
                } else if (line.startsWith("operations")) {
                    relationType.setOperations(readOperations());
                }
            }
            relationTypes.add(relationType);
        }
    }

    private void readComponents() throws IOException, InvalidPropertyValueException {
        line = reader.readLine().trim();
        while (reader.ready() && !line.startsWith("components")) {
            line = reader.readLine().trim();
        }
        line = reader.readLine().trim();
        while (line.startsWith("-")) {
            Component component = new Component();

            String[] split = line.split(":", 2);
            if (!split[1].isEmpty()) {
                component.setName(split[1].replaceAll("\\s|\"", ""));
            } else {
                component.setName(split[0].replaceFirst("- ", ""));
            }

            line = reader.readLine().trim();
            while (!line.startsWith("-") && !line.startsWith("relations")) {
                if (cachedLines.isEmpty()) {
                    if (line.startsWith("type")) {
                        String value = line.split(":", 2)[1].replaceAll("\\s|\"", "");
                        for (ComponentType componentType : componentTypes) {
                            if (componentType.getName().equals(value)) {
                                component.setType(componentType);
                            }
                        }
                        line = reader.readLine().trim();
                    } else if (line.startsWith("description")) {
                        String value = line.split(":", 2)[1].replaceAll("\\s|\"", "");
                        component.setDescription(value);
                        line = reader.readLine().trim();
                    } else if (line.startsWith("properties")) {
                        component.setProperties(readProperties());
                    } else if (line.startsWith("operations")) {
                        component.setOperations(readOperations());
                    } else if (line.startsWith("artifacts")) {
                        component.setArtifacts(readArtifacts());
                    }
                } else {
                    String value = cachedLines.get(0).split(":")[1].replaceAll("\\s|\"", "");
                    for (ComponentType componentType : componentTypes) {
                        if (componentType.getName().equals(value)) {
                            component.setType(componentType);
                        }
                    }
                    component.setDescription(cachedLines.get(1).split(":")[1].replaceAll("\\s", ""));
                    cachedLines.clear();
                }
            }
            component.setConfidence(Confidence.CONFIRMED);
            components.add(component);
        }
    }

    private void readRelations() throws IOException, InvalidRelationException, InvalidPropertyValueException {
        line = reader.readLine().trim();
        while (reader.ready() && !line.startsWith("relations")) {
            line = reader.readLine().trim();
        }
        line = reader.readLine().trim();
        while (line.startsWith("-")) {
            Relation relation = new Relation();

            String name = line.split(":", 2)[0].replaceFirst("- ", "");
            relation.setName(name);

            line = reader.readLine().trim();
            while (!line.startsWith("-") && !line.startsWith("component_types")) {
                if (line.startsWith("type")) {
                    String value = line.split(":", 2)[1].replaceAll("\\s|\"", "");
                    for (RelationType relationType : relationTypes) {
                        if (relationType.getName().equals(value)) {
                            relation.setType(relationType);
                        }
                    }
                    line = reader.readLine().trim();
                } else if (line.startsWith("description")) {
                    String value = line.split(":", 2)[1].replaceAll("\\s|\"", "");
                    relation.setDescription(value);
                    line = reader.readLine().trim();
                } else if (line.startsWith("source")) {
                    String value = line.split(":", 2)[1].replaceAll("\\s|\"", "");
                    for (Component component : components) {
                        if (component.getName().equals(value)) {
                            relation.setSource(component);
                        }
                    }
                    line = reader.readLine().trim();
                } else if (line.startsWith("target")) {
                    String value = line.split(":", 2)[1].replaceAll("\\s|\"", "");
                    for (Component component : components) {
                        if (component.getName().equals(value)) {
                            relation.setTarget(component);
                        }
                    }
                    line = reader.readLine().trim();
                } else if (line.startsWith("properties")) {
                    relation.setProperties(readProperties());
                } else if (line.startsWith("operations")) {
                    relation.setOperations(readOperations());
                }
            }
            relation.setConfidence(Confidence.CONFIRMED);
            relations.add(relation);
        }
    }

    private void parseFile(URL url) throws IOException, InvalidPropertyValueException, InvalidRelationException {
        reader = new BufferedReader(new InputStreamReader(url.openStream()));
        readGlobalProperties();
        readComponentTypes();
        readRelationTypes();
        reader.close();
        reader = new BufferedReader(new InputStreamReader(url.openStream()));
        readComponents();
        cachedLines.clear();
        reader.close();
        reader = new BufferedReader(new InputStreamReader(url.openStream()));
        readRelations();
        reader.close();
    }
}