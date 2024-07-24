package ust.tad.layoutpipeline.analysis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import ust.tad.layoutpipeline.analysistask.AnalysisTaskResponseSender;
import ust.tad.layoutpipeline.analysistask.Location;
import ust.tad.layoutpipeline.models.ModelsService;
import ust.tad.layoutpipeline.models.tadm.*;
import ust.tad.layoutpipeline.models.tsdm.InvalidAnnotationException;

@Service
public class AnalysisService {

    @Autowired
    ModelsService modelsService;

    @Autowired
    AnalysisTaskResponseSender analysisTaskResponseSender;

    LayoutService layoutService;

    private TechnologyAgnosticDeploymentModel tadm;

    private UUID transformationProcessId;

    private List<Property> properties = new ArrayList<>();
    private List<Component> components = new ArrayList<>();
    private List<Relation> relations = new ArrayList<>();
    private List<ComponentType> componentTypes = new ArrayList<>();
    private List<RelationType> relationTypes = new ArrayList<>();

    public void startAnalysis(UUID taskId, UUID transformationProcessId, List<String> commands, List<Location> locations) {
        this.tadm = modelsService.getTechnologyAgnosticDeploymentModel(transformationProcessId);

        if (!commands.isEmpty() || !locations.isEmpty()) {
            this.transformationProcessId = transformationProcessId;
            try {
                runAnalysis(locations);
                modelsService.updateTechnologyAgnosticDeploymentModel(tadm);
            } catch (IOException | InvalidAnnotationException | InvalidPropertyValueException | InvalidRelationException e) {
                e.printStackTrace();
                analysisTaskResponseSender.sendFailureResponse(taskId,
                        e.getClass() + ": " + e.getMessage());
                return;
            }
        }

        layoutService.generateLayout(this.tadm);

        analysisTaskResponseSender.sendSuccessResponse(taskId);
    }

    private void runAnalysis(List<Location> locations) throws IOException, InvalidAnnotationException, InvalidPropertyValueException, InvalidRelationException {
        for (Location location : locations) {
            if ("tf".equals(StringUtils.getFilenameExtension(location.getUrl().toString()))) {
                parseFile(location.getUrl());
            }
        }
        this.tadm = new TechnologyAgnosticDeploymentModel(transformationProcessId, properties, components, relations, componentTypes, relationTypes);
    }

    private List<Artifact> readArtifacts(BufferedReader reader, String nextLine) throws IOException {
        List<Artifact> artifacts = new ArrayList<>();
        if (nextLine.isEmpty()) {
            nextLine = reader.readLine();
        } else if (nextLine.startsWith("artifacts") && !nextLine.contains("[]")) {
            nextLine = reader.readLine();
            while (nextLine.startsWith("-")) {
                Artifact artifact = new Artifact();

                String type = nextLine.split(":")[0].replaceAll("\\s|-", "");
                artifact.setType(type);

                nextLine = reader.readLine();
                while (!nextLine.startsWith("-")) {
                    String key = nextLine.split(":")[0].replaceAll("\\s|-", "");
                    String value = nextLine.split(":")[1].replaceAll("\\s|-", "");

                    if (key.equals("name")) {
                        artifact.setName(value);
                    } else if (key.equals("fileURI")) {
                        artifact.setFileUri(URI.create(value));
                    }
                }

                artifact.setConfidence(Confidence.SUSPECTED);
                artifacts.add(artifact);
                nextLine = reader.readLine();
            }
        }
        return artifacts;
    }

    private  List<Operation> readOperations(BufferedReader reader, String nextLine) throws IOException {
        List<Operation> operations = new ArrayList<>();
        if (nextLine.isEmpty()) {
            nextLine = reader.readLine();
        } else if (nextLine.startsWith("operations") && !nextLine.contains("[]")) {
            //TODO: Gaining knowledge of structure.
        }
        return  operations;
    }

    private List<Property> readProperties(BufferedReader reader, String nextLine) throws IOException, InvalidPropertyValueException {
        List<Property> properties = new ArrayList<>();
        if (nextLine.isEmpty() || nextLine.equals("---")) {
            nextLine = reader.readLine();
        } else if (nextLine.startsWith("properties") && !nextLine.contains("[]")) {
            nextLine = reader.readLine();
            while (nextLine.startsWith("-")) {
                Property property = new Property();

                String key = nextLine.split(":")[0].replaceAll("\\s|-", "");
                String value = nextLine.split(":")[1].replaceAll("\\s|-", "");

                if (value.isEmpty()) {
                    property.setKey(key);
                    property.setValue(value);

                    nextLine = reader.readLine();
                    while (!nextLine.startsWith("-")){
                        nextLine = nextLine.replaceAll("\\s|-", "");
                        if (nextLine.startsWith("type")) {
                            switch (nextLine.split(":")[1].replaceAll("\"", "")) {
                                case "BOOLEAN":
                                    property.setType(PropertyType.BOOLEAN);
                                    break;
                                case "DOUBLE":
                                    property.setType(PropertyType.DOUBLE);
                                    break;
                                case "INTEGER":
                                    property.setType(PropertyType.INTEGER);
                                    break;
                                default:
                                    property.setType(PropertyType.STRING);
                                    break;
                            }
                        } else if (nextLine.startsWith("required")) {
                            property.setRequired(nextLine.split(":")[1].equals("true"));
                        }
                    }
                    property.setConfidence(Confidence.CONFIRMED);
                    properties.add(property);
                } else {
                    property.setKey(key);
                    property.setValue(value);
                    property.setType(PropertyType.STRING);
                    property.setRequired(false);
                    property.setConfidence(Confidence.SUSPECTED);
                    properties.add(property);
                }
                nextLine = reader.readLine();
            }
        }
        return properties;
    }

    private void readComponentTypes(BufferedReader reader) throws IOException, InvalidPropertyValueException {
        String nextLine = reader.readLine();
        while (!nextLine.startsWith("component_types")){
            nextLine = reader.readLine();
        }
        nextLine = reader.readLine();
        while (nextLine.startsWith("-")) {
            ComponentType componentType = new ComponentType();

            String name = nextLine.replaceAll("\\s|-|:", "");
            componentType.setName(name);

            nextLine = reader.readLine();
            while (!nextLine.startsWith("-")) {
                if (nextLine.startsWith("extends")) {
                    String value = nextLine.split(":")[1].replaceAll("\\s", "");
                    if (!value.equals("\"-\"")) {
                        for (ComponentType parentType : componentTypes) {
                            if (parentType.getName().equals(value.replaceAll("\"", ""))) {
                                componentType.setParentType(parentType);
                            }
                        }
                    }
                } else if (nextLine.startsWith("description")) {
                    String value = nextLine.split(":")[1].replaceAll("\\s", "");
                    componentType.setDescription(value);
                } else if (nextLine.startsWith("properties")) {
                    componentType.setProperties(readProperties(reader, nextLine));
                } else if (nextLine.startsWith("operations")) {
                    componentType.setOperations(readOperations(reader, nextLine));
                }
                nextLine = reader.readLine();
            }
            componentTypes.add(componentType);
            nextLine = reader.readLine();
        }
    }

    private void readRelationTypes(BufferedReader reader) throws IOException, InvalidPropertyValueException {
        String nextLine = reader.readLine();
        while (!nextLine.startsWith("relation_types")){
            nextLine = reader.readLine();
        }
        nextLine = reader.readLine();
        while (nextLine.startsWith("-")) {
            RelationType relationType = new RelationType();

            String name = nextLine.replaceAll("\\s|-|:", "");
            relationType.setName(name);

            nextLine = reader.readLine();
            while (!nextLine.startsWith("-")) {
                if (nextLine.startsWith("extends")) {
                    String value = nextLine.split(":")[1].replaceAll("\\s", "");
                    if (!value.equals("\"-\"")) {
                        for (RelationType parentType : relationTypes) {
                            if (parentType.getName().equals(value.replaceAll("\"", ""))) {
                                relationType.setParentType(parentType);
                            }
                        }
                    }
                } else if (nextLine.startsWith("description")) {
                    String value = nextLine.split(":")[1].replaceAll("\\s", "");
                    relationType.setDescription(value.replaceAll("\"", ""));
                } else if (nextLine.startsWith("properties")) {
                    relationType.setProperties(readProperties(reader, nextLine));
                } else if (nextLine.startsWith("operations")) {
                    relationType.setOperations(readOperations(reader, nextLine));
                }
                nextLine = reader.readLine();
            }
            relationTypes.add(relationType);
            nextLine = reader.readLine();
        }
    }

    private  void readComponents(BufferedReader reader) throws IOException, InvalidPropertyValueException {
        String nextLine = reader.readLine();
        if (nextLine.startsWith("components")) {
            nextLine = reader.readLine();
            while (nextLine.startsWith("-")) {
                Component component = new Component();

                String name = nextLine.replaceAll("\\s|-|:", "");
                component.setName(name);

                nextLine = reader.readLine();
                while (!nextLine.startsWith("-")) {
                    if (nextLine.startsWith("type")) {
                        String value = nextLine.split(":")[1].replaceAll("\\s", "");
                        for (ComponentType componentType : componentTypes) {
                            if (componentType.getName().equals(value.replaceAll("\"", ""))) {
                                component.setType(componentType);
                            }
                        }
                    } else if (nextLine.startsWith("description")) {
                        String value = nextLine.split(":")[1].replaceAll("\\s", "");
                        component.setDescription(value);
                    } else if (nextLine.startsWith("properties")) {
                        component.setProperties(readProperties(reader, nextLine));
                    } else if (nextLine.startsWith("operations")) {
                        component.setOperations(readOperations(reader, nextLine));
                    } else if (nextLine.startsWith("artifacts")) {
                        component.setArtifacts(readArtifacts(reader, nextLine));
                    }
                    nextLine = reader.readLine();
                }
                components.add(component);
                nextLine = reader.readLine();
            }
        }
    }

    private void readRelations(BufferedReader reader) throws IOException, InvalidRelationException, InvalidPropertyValueException {
        String nextLine = reader.readLine();
        while (!nextLine.startsWith("relations")) {
            nextLine = reader.readLine();
        }
        nextLine = reader.readLine();
        while (nextLine.startsWith("-")) {
            Relation relation = new Relation();

            String name = nextLine.replaceAll("\\s|-|:", "");
            relation.setName(name);
            relation.setId(name);

            nextLine = reader.readLine();
            while (!nextLine.startsWith("-")) {
                if (nextLine.startsWith("type")) {
                    String value = nextLine.split(":")[1].replaceAll("\\s", "");
                    for (RelationType relationType : relationTypes) {
                        if (relationType.getName().equals(value.replaceAll("\"", ""))) {
                            relation.setType(relationType);
                        }
                    }
                }
                else if (nextLine.startsWith("description")) {
                    String value = nextLine.split(":")[1].replaceAll("\\s", "");
                    relation.setDescription(value.replaceAll("\"", ""));
                }
                else if (nextLine.startsWith("source")) {
                    String value = nextLine.split(":")[1].replaceAll("\\s", "");
                    for (Component component : components) {
                        if (component.getName().equals(value.replaceAll("\"", ""))) {
                            relation.setSource(component);
                        }
                    }
                }
                else if (nextLine.startsWith("target")) {
                    String value = nextLine.split(":")[1].replaceAll("\\s", "");
                    for (Component component : components) {
                        if (component.getName().equals(value.replaceAll("\"", ""))) {
                            relation.setSource(component);
                        }
                    }
                }
                else if (nextLine.startsWith("properties")) {
                    relation.setProperties(readProperties(reader, nextLine));
                } else if (nextLine.startsWith("operations")) {
                    relation.setOperations(readOperations(reader, nextLine));
                }
                nextLine = reader.readLine();
            }
            relation.setConfidence(Confidence.SUSPECTED);
            relations.add(relation);
            nextLine = reader.readLine();
        }
    }

    private void parseFile(URL url) throws IOException, InvalidPropertyValueException, InvalidRelationException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
        String nextLine = reader.readLine();
        reader.mark(0);
        this.properties = readProperties(reader, nextLine);
        readComponentTypes(reader);
        readRelationTypes(reader);
        reader.reset();
        readComponents(reader);
        readRelations(reader);
        reader.close();
    }
}