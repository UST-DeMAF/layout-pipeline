package ust.tad.layoutpipeline.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ust.tad.layoutpipeline.models.tadm.*;
import ust.tad.layoutpipeline.registration.PluginRegistrationRunner;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class LayoutService {
    private static final Logger LOG = LoggerFactory.getLogger(PluginRegistrationRunner.class);

    private List<Component> components;
    private List<ComponentType> componentTypes;
    private List<Relation>  relations;
    private UUID transformationProcessId;

    private String path;
    private String file;

    private Map<String, float[]> layout = new HashMap<>();

    private class Node {
        String name;
        String type;
        float x;
        float y;
        String displayName;
        List<Property> properties;
        List<Requirement> requirements;
    }

    private class Requirement {
        String type;
        String node;
        String relationship;
        String capability;
    }

    void generateLayout(TechnologyAgnosticDeploymentModel tadm) {
        components = tadm.getComponents();
        componentTypes = tadm.getComponentTypes();
        relations = tadm.getRelations();
        transformationProcessId = tadm.getTransformationProcessId();

        path = "/var/repository/graphviz/";
        file = transformationProcessId.toString() +".dot";

        try {
            Files.createDirectories(Paths.get(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        createDotFile(components, relations, path + file);
        layout = callGraphVIZ(path + file);

        createNodeTypes(componentTypes, transformationProcessId);
        createServiceTemplate(components, relations, transformationProcessId);
    }

    void createDotFile(List<Component> components, List<Relation> relations, String path ) {
        List<String> nodes = new ArrayList<>();
        List<String> graph = new ArrayList<>();
        List<String> subgraph = new ArrayList<>();

        for (Component component : components) {
            nodes.add("\"" + component.getName() + "\" [shape=\"polygon\" width=2.5 height=0.8]" );
        }

        for (Relation relation: relations) {
            if (relation.getType().getName().equals("HostedOn")) {
                graph.add("\"" + relation.getSource().getName() + "\" -> \""+ relation.getTarget().getName() + "\" [label=\"HostedOn\"]");
            } else if (relation.getType().getName().equals("ConnectsTo")) {
                subgraph.add("\"" + relation.getSource().getName() + "\" -> \""+ relation.getTarget().getName() + "\" [label=\"ConnectsTo\" style=\"dashed\"]");
            } else {

            }
        }

        try (FileWriter writer = new FileWriter(path)) {
            writer.write("strict digraph {");
            for (String node : nodes){
                writer.write(node);
            }
            for (String relation : graph){
                writer.write(relation);
            }
            writer.write("subgraph {");
            for (String relation : subgraph){
                writer.write(relation);
            }
            writer.write("}}");
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    Map<String,float[]> callGraphVIZ(String path) {
        Map<String, float[]> output = new HashMap<>();
        String command = "dot -Tplain " + path;

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("sh", "-c", command);

        try {
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            float maxY = 0;
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("node")) {
                    String[] splits = line.split(" ");
                    String node = splits[1].replaceAll("\"", "");
                    float[] coords = {Float.parseFloat(splits[2]), Float.parseFloat(splits[3])};
                    maxY = coords[1] > maxY ? coords[1] : maxY;
                    output.put(node, coords);
                }
            }

            for (Map.Entry<String, float[]> entry : output.entrySet()) {
                float[] coords = entry.getValue();
                coords[0] = Math.round(coords[0] * 100);
                coords[1] = Math.round(Math.abs(coords[1] - maxY) * 100 + 100);
                entry.setValue(coords);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return output;
    }

    void createNodeTypes(List<ComponentType> componentTypes, UUID id ) {
        for (ComponentType componentType : componentTypes) {
            String nodeTypesPath = "/var/repository/nodetypes/ust.tad.nodetypes/" + id.toString() + "/" + componentType.getName() + "/";

            try {
                Files.createDirectories(Paths.get(nodeTypesPath));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            try (FileWriter writer = new FileWriter(nodeTypesPath + "NodeType.tosca")){
                writer.write("tosca_definitions_version: tosca_simple_yaml_1_3\n\nnode_types:\n");
                writer.write("ust.tad.nodetypes." + componentType.getName() + "\n");
                writer.write("derived_from: tosca.nodes.Root\nmetadata:\n");
                writer.write("targetNamespace: ust.tad.nodetypes\nabstract: \"false\"\nfinal: \"false\"\nproperties:\n");
                List<Property> properties = componentType.getProperties();
                for (Property property : properties) {
                    writer.write(property.getKey() + ":\n");
                    writer.write("type: " + property.getType().name() + "\n");
                    writer.write("required: " + property.getRequired() + "\n");
                    writer.write("default: " + property.getValue().toString() + "\n");
                }
                writer.write("requirements:\n - host:\n");
                writer.write(" capability: tosca.capabilities.Node\n");
                writer.write(" relationship: tosca.relationships.HostedOn\n");
                writer.write("occurrences: [ 1, 1 ]\n");
                writer.write("interfaces:\n Standard:\n");
                writer.write("type: tosca.interfaces.node.lifecycle.Standard\noperations:\n");
                writer.write("stop:\ndescription: The standard stop operation\n");
                writer.write("start:\ndescription: The standard start operation\n");
                writer.write("create:\ndescription: The standard create operation\n");
                writer.write("configure:\ndescription: The standard configure operation\n");
                writer.write("delete:\ndescription: The standard delete operation\n");
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    void createServiceTemplate(List<Component> components, List<Relation> relations, UUID id) {
        Map<String, Node> nodes = new HashMap<>();
        Map<String, Integer> typeCount = new HashMap<>();

        for (Component component : components) {
            Node node = new Node();
            int count = 0;

            try {
                node.type = component.getType().getName();
            } catch (Exception e) {
                LOG.info("Component type of the component " + component.getName() + " is not defined.");
            }

            if(typeCount.containsKey(node.type)) {
                count = typeCount.get(node.type) + 1;
                typeCount.replace(node.type, count);
            } else {
                typeCount.put(node.type, count);
            }

            node.displayName = component.getName();
            node.name = component.getName() + "_" + count;
            node.properties = component.getProperties();

            float[] coords = layout.get(node.displayName);
            node.x = coords[0];
            node.y = coords[1];

            List<Requirement> requirements = new ArrayList<>();
            for(Relation relation : relations) {
                Requirement requirement = new Requirement();
                if(relation.getSource().getName().equals(node.displayName)) {
                    requirement.type = relation.getType().getName();
                    requirement.node = relation.getTarget().getName(); // Target node displayName
                    requirement.relationship = relation.getName();
                    requirement.capability = "feature";
                    requirements.add(requirement);
                }
            }
            node.requirements = requirements;
            nodes.put(node.displayName, node);
        }

        String serviceTemplatePath = "/var/repository/servicetemplates/ust.tad.servicetemplates/" + id.toString() + "/";

        try {
            Files.createDirectories(Paths.get(serviceTemplatePath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (FileWriter writer = new FileWriter(serviceTemplatePath + "ServiceTemplate.tosca")) {
            writer.write("tosca_definitions_version: tosca_simple_yaml_1_3\n\nmetadata:\n");
            writer.write("targetNamespace: \"ust.tad.servicetemplates\"\n");
            writer.write("name: " + id.toString() + "\n");
            writer.write("topology_template:\nnode_templates:\n");
            for (Node node : nodes.values()) {
                writer.write(node.name + ":\n");
                writer.write("type: ust.tad.nodetypes." + node.type + "\n");
                writer.write("metadata:\n");
                writer.write("x: '" + node.x + "'\n");
                writer.write("y: '" + node.y + "'\n");
                writer.write("displayName: " + node.displayName + "\n");
                writer.write("properties:\n");
                for (Property property : node.properties) {
                    writer.write(property.getKey() + ": " + property.getValue() + "\n");
                }
                if (!node.requirements.isEmpty()) {
                    writer.write("requirements:\n");
                    for (Requirement requirement : node.requirements) {
                        if (requirement.type.equals("HostedOn")) {
                            writer.write("- host:\n");
                        } else if (requirement.type.equals("ConnectsTo")) {
                            writer.write("- connect:\n");
                        }
                        writer.write("node: " + nodes.get(requirement.node).name + "\n");
                        writer.write("relationship: " + requirement.relationship + "\n");
                        writer.write("capability: " + requirement.capability + "\n");
                    }
                }
                writer.write("\n");
            }
            writer.write("relationship_templates: \n");
            for (Relation relation : relations) {
                writer.write(relation.getName() + ":\n");
                writer.write("type: tosca.relationships." + relation.getType().getName() + "\n");
            }
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
