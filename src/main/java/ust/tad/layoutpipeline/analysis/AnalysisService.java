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

    LayoutService layoutService;

    private static final Logger LOG = LoggerFactory.getLogger(PluginRegistrationRunner.class);

    private static final Set<String> supportedFileExtensions = Set.of("yaml", "yml");

    private BufferedReader reader;
    private String line;

    private TechnologyAgnosticDeploymentModel tadm;

    private UUID transformationProcessId;

    private List<Property> properties = new ArrayList<>();
    private List<Component> components = new ArrayList<>();
    private List<Relation> relations = new ArrayList<>();
    private List<ComponentType> componentTypes = new ArrayList<>();
    private List<RelationType> relationTypes = new ArrayList<>();

    public void startAnalysis(UUID taskId, UUID transformationProcessId, List<String> commands, List<Location> locations) {
        LOG.info("1");
        this.tadm = modelsService.getTechnologyAgnosticDeploymentModel(transformationProcessId);

        if (!commands.isEmpty() && !locations.isEmpty()) {
            LOG.info("2");
            this.transformationProcessId = transformationProcessId;
            try {
                LOG.info("3");
                runAnalysis(locations);
                LOG.info("4");
                modelsService.updateTechnologyAgnosticDeploymentModel(tadm);
            } catch (IOException | InvalidAnnotationException | InvalidPropertyValueException | InvalidRelationException e) {
                e.printStackTrace();
                analysisTaskResponseSender.sendFailureResponse(taskId,
                        e.getClass() + ": " + e.getMessage());
                return;
            }
        }
        LOG.info("5");
        layoutService.generateLayout(this.tadm);
        LOG.info("6");
        analysisTaskResponseSender.sendSuccessResponse(taskId);
    }

    private void runAnalysis(List<Location> locations) throws IOException, InvalidAnnotationException, InvalidPropertyValueException, InvalidRelationException {
        for (Location location : locations) {
            LOG.info("7");
            String fileExtension = StringUtils.getFilenameExtension(location.getUrl().toString());
            if (supportedFileExtensions.contains(fileExtension)) {
                LOG.info("8");
                parseFile(location.getUrl());
            }
        }
        LOG.info("9");
        this.tadm = new TechnologyAgnosticDeploymentModel(transformationProcessId, properties, components, relations, componentTypes, relationTypes);
    }

    private List<Artifact> readArtifacts() throws IOException {
        LOG.info("10");
        List<Artifact> artifacts = new ArrayList<>();
        if (line.startsWith("artifacts") && !line.contains("[]")) {
            LOG.info("11");
            line = reader.readLine().trim();
            while (line.startsWith("-")) {
                Artifact artifact = new Artifact();
                LOG.info("12");
                String type = line.split(":")[0].replaceAll("\\s|-", "");
                artifact.setType(type);

                line = reader.readLine().trim();
                while (!line.startsWith("-") && !line.startsWith("relations")) {
                    LOG.info("13");
                    String key = line.split(":")[0].trim();
                    String value = line.split(":")[1].trim().replaceAll("\"", "");

                    if (key.equals("name")) {
                        LOG.info("14");
                        artifact.setName(value);
                    } else if (key.equals("fileURI") && !value.equals("-")) {
                        LOG.info("15");
                        artifact.setFileUri(URI.create(value));
                    }
                    line = reader.readLine().trim();
                }
                LOG.info("16");
                artifact.setConfidence(Confidence.SUSPECTED);
                artifacts.add(artifact);
                line = reader.readLine().trim();
            }
        } else {
            line = reader.readLine().trim();
        }
        return artifacts;
    }

    private  List<Operation> readOperations() throws IOException {
        LOG.info("17");
        List<Operation> operations = new ArrayList<>();
        if (line.startsWith("operations") && !line.contains("[]")) {
            LOG.info("18");
            //TODO: Gaining knowledge of structure.
        } else {
            line = reader.readLine().trim();
        }
        return  operations;
    }

    private  void readGlobalProperties() throws IOException, InvalidPropertyValueException {
        line = reader.readLine().trim();
        LOG.info("19");
        while (reader.ready() && !line.startsWith("properties")) {
            line = reader.readLine().trim();
        }
        properties = readProperties();
    }

    private List<Property> readProperties() throws IOException, InvalidPropertyValueException {
        LOG.info("20");
        List<Property> properties = new ArrayList<>();
        if (line.startsWith("properties") && !line.contains("[]")) {
            line = reader.readLine().trim();
            while (line.startsWith("-")) {
                Property property = new Property();
                LOG.info("21");
                String[] split = line.split(":");
                if (split.length == 2) {
                    LOG.info("22");
                    property.setKey(split[0].replaceAll("\\s|-", ""));
                    property.setValue(split[1].replaceAll("\\s|-", ""));
                    property.setType(PropertyType.STRING);
                    property.setRequired(false);
                    property.setConfidence(Confidence.SUSPECTED);
                    line = reader.readLine().trim();
                } else {
                    LOG.info("23");
                    property.setKey(split[0].replaceAll("\\s|-", ""));
                    line = reader.readLine().trim();
                    while (reader.ready() && !line.startsWith("-") && !line.startsWith("operations")) {
                        if (line.startsWith("type")) {
                            LOG.info("24");
                            switch (line.split(":")[1].replaceAll("\\s|\"", "")) {
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
                        } else if (line.startsWith("required")) {
                            LOG.info("25");
                            property.setRequired(line.split(":")[1].equals("true"));
                        }
                        line = reader.readLine().trim();
                    }
                    property.setConfidence(Confidence.CONFIRMED);
                }
                LOG.info("26");
                properties.add(property);
            }
        } else {
            line = reader.readLine().trim();
        }
        return properties;
    }

    private void readComponentTypes() throws IOException, InvalidPropertyValueException {
        LOG.info("27");
        line = reader.readLine().trim();
        while (reader.ready() && !line.startsWith("component_types")){
            line = reader.readLine().trim();
        }
        LOG.info("28");
        line = reader.readLine().trim();
        while (line.startsWith("-")) {
            ComponentType componentType = new ComponentType();

            LOG.info("29");
            String name = line.replaceAll("\\s|-|:", "");
            componentType.setName(name);

            line = reader.readLine().trim();
            while (!line.startsWith("-") && !line.startsWith("relation_types")) {
                if (line.startsWith("extends")) {
                    LOG.info("30");
                    String value = line.split(":")[1].replaceAll("\\s|\"", "");
                    if (!value.equals("\"-\"")) {
                        LOG.info("31");
                        for (ComponentType parentType : componentTypes) {
                            if (parentType.getName().equals(value)) {
                                componentType.setParentType(parentType);
                            }
                        }
                    }
                    line = reader.readLine().trim();
                } else if (line.startsWith("description")) {
                    LOG.info("32");
                    String value = line.split(":")[1].trim().replaceAll("\"", "");
                    componentType.setDescription(value);
                    line = reader.readLine().trim();
                } else if (line.startsWith("properties")) {
                    LOG.info("33");
                    componentType.setProperties(readProperties());
                } else if (line.startsWith("operations")) {
                    LOG.info("34");
                    componentType.setOperations(readOperations());
                }
            }
            LOG.info("35");
            componentTypes.add(componentType);
        }
    }

    private void readRelationTypes() throws IOException, InvalidPropertyValueException {
        LOG.info("36");
        while (reader.ready() && !line.startsWith("relation_types")){
            line = reader.readLine().trim();
        }
        LOG.info("37");
        line = reader.readLine().trim();
        while (line.startsWith("-")) {
            RelationType relationType = new RelationType();
            LOG.info("38");
            String name = line.replaceAll("\\s|-|:", "");
            relationType.setName(name);

            line = reader.readLine().trim();
            while (reader.ready() && !line.startsWith("-")) {
                if (line.startsWith("extends")) {
                    LOG.info("39");
                    String value = line.split(":")[1].replaceAll("\\s|\"", "");
                    if (!value.equals("\"-\"")) {
                        for (RelationType parentType : relationTypes) {
                            if (parentType.getName().equals(value)) {
                                relationType.setParentType(parentType);
                            }
                        }
                    }
                    line = reader.readLine().trim();
                } else if (line.startsWith("description")) {
                    LOG.info("40");
                    String value = line.split(":")[1].trim();
                    relationType.setDescription(value.replaceAll("\\s|\"", ""));
                    line = reader.readLine().trim();
                } else if (line.startsWith("properties")) {
                    LOG.info("41");
                    relationType.setProperties(readProperties());
                } else if (line.startsWith("operations")) {
                    LOG.info("42");
                    relationType.setOperations(readOperations());
                }
            }
            LOG.info("43");
            relationTypes.add(relationType);
        }
    }

    private  void readComponents() throws IOException, InvalidPropertyValueException {
        line = reader.readLine().trim();
        while (reader.ready() && !line.startsWith("components")) {
            line = reader.readLine().trim();
        }
        LOG.info("44");
        line = reader.readLine().trim();
        while (line.startsWith("-")) {
            Component component = new Component();

            String name = line.replaceAll("\\s|-|:", "");
            component.setName(name);

            line = reader.readLine().trim();
            while (!line.startsWith("-") && !line.startsWith("relations")) {
                if (line.startsWith("type")) {
                    LOG.info("45");
                    String value = line.split(":")[1].replaceAll("\\s|\"", "");
                    for (ComponentType componentType : componentTypes) {
                        if (componentType.getName().equals(value)) {
                            component.setType(componentType);
                        }
                    }
                    line = reader.readLine().trim();
                } else if (line.startsWith("description")) {
                    LOG.info("46");
                    String value = line.split(":")[1].replaceAll("\\s", "");
                    component.setDescription(value);
                    line = reader.readLine().trim();
                } else if (line.startsWith("properties")) {
                    LOG.info("47");
                    component.setProperties(readProperties());
                } else if (line.startsWith("operations")) {
                    LOG.info("48");
                    component.setOperations(readOperations());
                } else if (line.startsWith("artifacts")) {
                    LOG.info("49");
                    component.setArtifacts(readArtifacts());
                }
            }
            LOG.info("50");
            components.add(component);
        }
    }

    private void readRelations() throws IOException, InvalidRelationException, InvalidPropertyValueException {
        LOG.info("51");
        String line = reader.readLine().trim();
        while (reader.ready() && !line.startsWith("relations")) {
            line = reader.readLine().trim();
        }
        LOG.info("52");
        line = reader.readLine().trim();
        while (line.startsWith("-")) {
            Relation relation = new Relation();

            String name = line.replaceAll("\\s|-|:", "");
            relation.setName(name);
            relation.setId(name);

            line = reader.readLine().trim();
            while (!line.startsWith("-")) {
                if (line.startsWith("type")) {
                    LOG.info("53");
                    String value = line.split(":")[1].replaceAll("\\s|\"", "");
                    for (RelationType relationType : relationTypes) {
                        if (relationType.getName().equals(value)) {
                            relation.setType(relationType);
                        }
                    }
                    line = reader.readLine().trim();
                }
                else if (line.startsWith("description")) {
                    LOG.info("54");
                    String value = line.split(":")[1].replaceAll("\\s|\"", "");
                    relation.setDescription(value);
                    line = reader.readLine().trim();
                }
                else if (line.startsWith("source")) {
                    LOG.info("55");
                    String value = line.split(":")[1].replaceAll("\\s|\"", "");
                    for (Component component : components) {
                        if (component.getName().equals(value)) {
                            relation.setSource(component);
                        }
                    }
                    line = reader.readLine().trim();
                }
                else if (line.startsWith("target")) {
                    LOG.info("56");
                    String value = line.split(":")[1].replaceAll("\\s|\"", "");
                    for (Component component : components) {
                        if (component.getName().equals(value)) {
                            relation.setSource(component);
                        }
                    }
                    line = reader.readLine().trim();
                }
                else if (line.startsWith("properties")) {
                    LOG.info("57");
                    relation.setProperties(readProperties());
                } else if (line.startsWith("operations")) {
                    LOG.info("58");
                    relation.setOperations(readOperations());
                }
            }
            LOG.info("59");
            relation.setConfidence(Confidence.SUSPECTED);
            relations.add(relation);
        }
    }

    private void parseFile(URL url) throws IOException, InvalidPropertyValueException, InvalidRelationException {
        LOG.info("60");
        reader = new BufferedReader(new InputStreamReader(url.openStream()));
        readGlobalProperties();
        readComponentTypes();
        readRelationTypes();
        reader.close();
        LOG.info("61");
        reader = new BufferedReader(new InputStreamReader(url.openStream()));
        readComponents();
        readRelations();
        reader.close();
        LOG.info("62");
    }
}