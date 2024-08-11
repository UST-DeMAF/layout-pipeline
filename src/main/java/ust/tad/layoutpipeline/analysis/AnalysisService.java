package ust.tad.layoutpipeline.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;
import ust.tad.layoutpipeline.analysistask.AnalysisTaskResponseSender;
import ust.tad.layoutpipeline.analysistask.Location;
import ust.tad.layoutpipeline.models.ModelsService;
import ust.tad.layoutpipeline.models.tadm.*;
import ust.tad.layoutpipeline.models.tsdm.InvalidAnnotationException;
import ust.tad.layoutpipeline.registration.PluginRegistrationRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.*;

@Service
public class AnalysisService {

    private static final Logger LOG = LoggerFactory.getLogger(PluginRegistrationRunner.class);
    private static final Set<String> supportedFileExtensions = Set.of("yaml", "yml");

    private final List<Component> components = new ArrayList<>();
    private final List<Relation> relations = new ArrayList<>();
    private final List<ComponentType> componentTypes = new ArrayList<>();
    private final List<RelationType> relationTypes = new ArrayList<>();

    @Autowired
    ModelsService modelsService;

    @Autowired
    AnalysisTaskResponseSender analysisTaskResponseSender;

    @Autowired
    LayoutService layoutService;

    private List<Property> properties = new ArrayList<>();
    private TechnologyAgnosticDeploymentModel tadm;
    private UUID transformationProcessId;

    /*
     * Start the analysis process for the given task.
     * @param taskId the task id
     * @param transformationProcessId the transformation process id
     * @param commands the commands
     * @param locations the locations
     */
    public void startAnalysis(UUID taskId, UUID transformationProcessId, List<String> commands, List<Location> locations) {
        this.tadm = modelsService.getTechnologyAgnosticDeploymentModel(transformationProcessId);

        if (!commands.isEmpty() && !locations.isEmpty()) {
            this.transformationProcessId = transformationProcessId;
            try {
                runAnalysis(locations);
            } catch (IOException | InvalidAnnotationException | InvalidPropertyValueException |
                     InvalidRelationException e) {
                e.printStackTrace();
                analysisTaskResponseSender.sendFailureResponse(taskId, e.getClass() + ": " + e.getMessage());
                return;
            }
            //modelsService.updateTechnologyAgnosticDeploymentModel(this.tadm);
        }
        layoutService.generateLayout(this.tadm);
        analysisTaskResponseSender.sendSuccessResponse(taskId);
    }

    /*
     * Run the analysis for the given locations.
     * @param locations the locations
     */
    private void runAnalysis(List<Location> locations) throws IOException, InvalidAnnotationException, InvalidPropertyValueException, InvalidRelationException {
        for (Location location : locations) {
            String fileExtension = StringUtils.getFilenameExtension(location.getUrl().toString());
            if (supportedFileExtensions.contains(fileExtension)) {
                parseFile(location.getUrl());
            }
        }
        this.tadm = new TechnologyAgnosticDeploymentModel(transformationProcessId, properties, components, relations, componentTypes, relationTypes);
    }

    /*
     * Check if the given string is numeric.
     * @param str the string
     * @return true if the string is numeric, false otherwise
     */
    public boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NullPointerException | NumberFormatException e) {
            return false;
        }
    }

    /*
     * Read the artifacts from the given object.
     * @param obj the object
     * @return the list of artifacts
     */
    private List<Artifact> readArtifacts(Object obj) {
        List<Artifact> artifacts = new ArrayList<>();
        String fileURI, key;
        Object value;

        if (obj instanceof List) {
            List<Map<String, Object>> list = (List<Map<String, Object>>) obj;
            for (Map<String, Object> map : list) {
                Artifact artifact = new Artifact();

                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    key = entry.getKey();
                    value = entry.getValue();

                    switch (key) {
                        case "type":
                            artifact.setType(value.toString());
                            break;
                        case "name":
                            artifact.setName(value.toString());
                            break;
                        case "fileURI":
                            if (Objects.nonNull(value)) {
                                fileURI = value.toString();
                                if (!fileURI.isEmpty() && !fileURI.equals("-")) {
                                    artifact.setFileUri(URI.create(value.toString()));
                                }
                            }
                            break;
                    }
                }
                artifact.setConfidence(Confidence.CONFIRMED);
                artifacts.add(artifact);
            }
        }
        return artifacts;
    }

    /*
     * Read the operations from the given object.
     * @param obj the object
     * @return the list of operations
     */
    private List<Operation> readOperations(Object obj) {
        List<Operation> operations = new ArrayList<>();
        String key;
        Object value;

        if (obj instanceof List) {
            List<Map<String, Object>> list = (List<Map<String, Object>>) obj;
            for (Map<String, Object> map : list) {
                Operation operation = new Operation();

                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    key = entry.getKey();
                    value = entry.getValue();

                    switch (key) {
                        case "name":
                            operation.setName(value.toString());
                            break;
                        case "artifacts":
                            operation.setArtifacts(readArtifacts(value));
                    }
                }
                operation.setConfidence(Confidence.CONFIRMED);
                operations.add(operation);
            }
        }
        return operations;
    }

    /*
     * Read the properties from the given object.
     * @param obj the object
     * @return the list of properties
     */
    private List<Property> readProperties(Object obj) throws InvalidPropertyValueException {
        List<Property> properties = new ArrayList<>();
        String key;
        Object value;

        if (obj instanceof List) {
            List<Map<String, Object>> list = (List<Map<String, Object>>) obj;
            for (Map<String, Object> map : list) {
                Property property = new Property();

                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    key = entry.getKey();
                    value = entry.getValue();

                    switch (key) {
                        case "key":
                            property.setKey(value.toString());
                            break;
                        case "type":
                            switch (value.toString()) {
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
                            break;
                        case "default_value":
                        case "value":
                            property.setValue(value);
                            break;
                        case "required":
                            property.setRequired(Boolean.parseBoolean(value.toString()));
                            break;
                        default:
                            if (isNumeric(key)) {
                                property.setKey("\"" + key + "\"");
                            } else {
                                property.setKey(key);
                            }

                            if (value instanceof Map) {
                                Map<String, Object> valueMap = (Map<String, Object>) value;
                                for (Map.Entry<String, Object> valueEntry : valueMap.entrySet()) {
                                    key = valueEntry.getKey();
                                    value = valueEntry.getValue();

                                    switch (key) {
                                        case "type":
                                            switch (value.toString()) {
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
                                            break;
                                        case "required":
                                            property.setRequired(Boolean.parseBoolean(value.toString()));
                                            break;
                                    }
                                }

                            } else {
                                switch (value.getClass().getSimpleName()) {
                                    case "Boolean":
                                        property.setType(PropertyType.BOOLEAN);
                                        property.setValue(false);
                                        break;
                                    case "Double":
                                        property.setType(PropertyType.DOUBLE);
                                        property.setValue(0.0);
                                        break;
                                    case "Integer":
                                        property.setType(PropertyType.INTEGER);
                                        property.setValue(0);
                                        break;
                                    default:
                                        property.setType(PropertyType.STRING);
                                        property.setValue("");
                                        break;
                                }
                                property.setValue(value);
                                property.setRequired(false);
                            }
                            break;
                    }
                }
                property.setConfidence(Confidence.CONFIRMED);
                properties.add(property);
            }
        }
        return properties;
    }

    /*
     * Read the component types from the given object.
     * @param obj the object
     */
    private void readComponentTypes(Object obj) throws InvalidPropertyValueException {
        String key, parent;
        Object value;

        if (obj instanceof List) {
            List<Map<String, Object>> list = (List<Map<String, Object>>) obj;
            for (Map<String, Object> map : list) {
                ComponentType componentType = new ComponentType();

                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    key = entry.getKey();
                    value = entry.getValue();

                    switch (key) {
                        case "name":
                            componentType.setName(value.toString());
                            break;
                        case "extends":
                            if (Objects.nonNull(value)) {
                                parent = value.toString();
                                if (!parent.isEmpty() && !parent.equals("-")) {
                                    for (ComponentType parentType : componentTypes) {
                                        if (parentType.getName().equals(value.toString())) {
                                            componentType.setParentType(parentType);
                                            break;
                                        }
                                    }
                                }
                            }
                            break;
                        case "description":
                            if (Objects.nonNull(value)) {
                                componentType.setDescription(value.toString());
                            }
                            break;
                        case "properties":
                            componentType.setProperties(readProperties(value));
                            break;
                        case "operations":
                            componentType.setOperations(readOperations(value));
                            break;
                        default:
                            componentType.setName(key);
                            if (value instanceof Map) {
                                Map<String, Object> valueMap = (Map<String, Object>) value;
                                for (Map.Entry<String, Object> valueEntry : valueMap.entrySet()) {
                                    key = valueEntry.getKey();
                                    value = valueEntry.getValue();

                                    switch (key) {
                                        case "extends":
                                            if (Objects.nonNull(value)) {
                                                parent = value.toString();
                                                if (!parent.isEmpty() && !parent.equals("-")) {
                                                    for (ComponentType parentType : componentTypes) {
                                                        if (parentType.getName().equals(value.toString())) {
                                                            componentType.setParentType(parentType);
                                                            break;
                                                        }
                                                    }
                                                }
                                            }
                                            break;
                                        case "description":
                                            if (Objects.nonNull(value)) {
                                                componentType.setDescription(value.toString());
                                            }
                                            break;
                                        case "properties":
                                            componentType.setProperties(readProperties(value));
                                            break;
                                        case "operations":
                                            componentType.setOperations(readOperations(value));
                                            break;
                                    }
                                }
                            }
                            break;
                    }
                }
                componentTypes.add(componentType);
            }
        }
    }

    /*
     * Read the relation types from the given object.
     * @param obj the object
     */
    private void readRelationTypes(Object obj) throws InvalidPropertyValueException {
        String key, parent;
        Object value;

        if (obj instanceof List) {
            List<Map<String, Object>> list = (List<Map<String, Object>>) obj;
            for (Map<String, Object> map : list) {
                RelationType relationType = new RelationType();

                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    key = entry.getKey();
                    value = entry.getValue();

                    relationType.setName(key);
                    if (value instanceof Map) {
                        Map<String, Object> valueMap = (Map<String, Object>) value;
                        for (Map.Entry<String, Object> valueEntry : valueMap.entrySet()) {
                            key = valueEntry.getKey();
                            value = valueEntry.getValue();
                            switch (key) {
                                case "extends":
                                    if (Objects.nonNull(value)) {
                                        parent = value.toString();
                                        if (!parent.isEmpty() && !parent.equals("-")) {
                                            for (RelationType parentType : relationTypes) {
                                                if (parentType.getName().equals(value.toString())) {
                                                    relationType.setParentType(parentType);
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                    break;
                                case "description":
                                    if (Objects.nonNull(value)) {
                                        relationType.setDescription(value.toString());
                                    }
                                    break;
                                case "properties":
                                    relationType.setProperties(readProperties(value));
                                    break;
                                case "operations":
                                    relationType.setOperations(readOperations(value));
                                    break;
                            }
                        }
                    }
                }
                relationTypes.add(relationType);
            }
        }
    }

    /*
     * Read the components from the given object.
     * @param obj the object
     */
    private void readComponents(Object obj) throws InvalidPropertyValueException {
        String key, type;
        Object value;

        if (obj instanceof List) {
            List<Map<String, Object>> list = (List<Map<String, Object>>) obj;
            for (Map<String, Object> map : list) {
                Component component = new Component();

                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    key = entry.getKey();
                    value = entry.getValue();
                    switch (key) {
                        case "name":
                            component.setName(value.toString());
                            break;
                        case "type":
                            if (Objects.nonNull(value)) {
                                type = value.toString();
                                if (!type.isEmpty() && !type.equals("-")) {
                                    for (ComponentType componentType : componentTypes) {
                                        if (componentType.getName().equals(type)) {
                                            component.setType(componentType);
                                            break;
                                        }
                                    }
                                }
                            }
                            break;
                        case "description":
                            if (Objects.nonNull(value)) {
                                component.setDescription(value.toString());
                            }
                            break;
                        case "properties":
                            component.setProperties(readProperties(value));
                            break;
                        case "operations":
                            component.setOperations(readOperations(value));
                            break;
                        case "artifacts":
                            component.setArtifacts(readArtifacts(value));
                            break;
                        default:
                            component.setName(key);
                            if (value instanceof Map) {
                                Map<String, Object> valueMap = (Map<String, Object>) value;
                                for (Map.Entry<String, Object> valueEntry : valueMap.entrySet()) {
                                    key = valueEntry.getKey();
                                    value = valueEntry.getValue();

                                    switch (key) {
                                        case "type":
                                            if (Objects.nonNull(value)) {
                                                type = value.toString();
                                                if (!type.isEmpty() && !type.equals("-")) {
                                                    for (ComponentType componentType : componentTypes) {
                                                        if (componentType.getName().equals(type)) {
                                                            component.setType(componentType);
                                                            break;
                                                        }
                                                    }
                                                }
                                            }
                                            break;
                                        case "description":
                                            if (Objects.nonNull(value)) {
                                                component.setDescription(value.toString());
                                            }
                                            break;
                                        case "properties":
                                            component.setProperties(readProperties(value));
                                            break;
                                        case "operations":
                                            component.setOperations(readOperations(value));
                                            break;
                                        case "artifacts":
                                            component.setArtifacts(readArtifacts(value));
                                            break;
                                    }
                                }
                            }
                            break;
                    }
                }
                component.setConfidence(Confidence.CONFIRMED);
                components.add(component);
            }
        }
    }

    /*
     * Read the relations from the given object.
     * @param obj the object
     */
    private void readRelations(Object obj) throws InvalidRelationException, InvalidPropertyValueException {
        String key, source, target, type;
        Object value;

        if (obj instanceof List) {
            List<Map<String, Object>> list = (List<Map<String, Object>>) obj;
            for (Map<String, Object> map : list) {
                Relation relation = new Relation();

                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    key = entry.getKey();
                    value = entry.getValue();

                    relation.setName(key);
                    if (value instanceof Map) {
                        Map<String, Object> valueMap = (Map<String, Object>) value;
                        for (Map.Entry<String, Object> valueEntry : valueMap.entrySet()) {
                            key = valueEntry.getKey();
                            value = valueEntry.getValue();

                            switch (key) {
                                case "type":
                                    if (Objects.nonNull(value)) {
                                        type = value.toString();
                                        if (!type.isEmpty() && !type.equals("-")) {
                                            for (RelationType relationType : relationTypes) {
                                                if (relationType.getName().equals(type)) {
                                                    relation.setType(relationType);
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                    break;
                                case "description":
                                    if (Objects.nonNull(value)) {
                                        relation.setDescription(value.toString());
                                    }
                                    break;
                                case "source":
                                    if (Objects.nonNull(value)) {
                                        source = value.toString();
                                        if (!source.isEmpty() && !source.equals("-")) {
                                            for (Component component : components) {
                                                if (component.getName().equals(source)) {
                                                    relation.setSource(component);
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                    break;
                                case "target":
                                    if (Objects.nonNull(value)) {
                                        target = value.toString();
                                        if (!target.isEmpty() && !target.equals("-")) {
                                            for (Component component : components) {
                                                if (component.getName().equals(target)) {
                                                    relation.setTarget(component);
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                    break;
                                case "properties":
                                    relation.setProperties(readProperties(value));
                                    break;
                                case "operations":
                                    relation.setOperations(readOperations(value));
                                    break;
                            }
                        }
                    }
                }
                relation.setConfidence(Confidence.CONFIRMED);
                relations.add(relation);
            }
        }
    }

    /*
     * Parse the file from the given URL.
     * @param url the URL
     */
    private void parseFile(URL url) throws IOException, InvalidPropertyValueException, InvalidRelationException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
        Map<String, Object> parsedYaml = new Yaml().load(reader);
        reader.close();

        properties = readProperties(parsedYaml.get("properties"));
        readComponentTypes(parsedYaml.get("component_types"));
        readRelationTypes(parsedYaml.get("relation_types"));
        readComponents(parsedYaml.get("components"));
        readRelations(parsedYaml.get("relations"));
    }
}