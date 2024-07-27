package ust.tad.layoutpipeline.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ust.tad.layoutpipeline.models.tadm.*;
import ust.tad.layoutpipeline.registration.PluginRegistrationRunner;

import java.io.*;
import java.util.*;

public class LayoutService {
    private static final Logger LOG = LoggerFactory.getLogger(PluginRegistrationRunner.class);

    private List<Component> components;
    private List<ComponentType> componentTypes;
    private List<Relation>  relations;
    private UUID transformationProcessId;

    private String dotPath;

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
        relations = tadm.getRelations();
        transformationProcessId = tadm.getTransformationProcessId();

        dotPath = "/app/target/data/inputs/" + transformationProcessId.toString() +".dot";

        createDotFile(components, relations, dotPath);
        layout = callGraphVIZ(dotPath);

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

            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("node")) {
                    String[] splits = line.split(" ");
                    String node = splits[1];
                    float[] coords = {Float.parseFloat(splits[2]), Float.parseFloat(splits[3])};
                    output.put(node, coords);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return output;
    }

    void createNodeTypes(List<ComponentType> componentTypes, UUID id ) {
        for (ComponentType componentType : componentTypes) {
            try (FileWriter writer = new FileWriter("/var/repository/nodetypes/ust.tad.nodetypes/" + id.toString() + "/" + componentType.getName() + "/NodeType.tosca")){
                writer.write("tosca_definitions_version: tosca_simple_yaml_1_3\n\nnode_types:");
                writer.write("ust.tad.nodetypes." + componentType.getName());
                writer.write("derived_from: tosca.nodes.Root\nmetadata:");
                writer.write("targetNamespace: ust.tad.nodetypes\nabstract: \"false\"\nfinal: \"false\"\nproperties:");
                List<Property> properties = componentType.getProperties();
                for (Property property : properties) {
                    writer.write(property.getKey() + ":");
                    writer.write("type: " + property.getType().name());
                    writer.write("required: " + property.getRequired());
                    writer.write("default: " + property.getValue().toString());
                }
                writer.write("requirements:\n - host:");
                writer.write(" capability: tosca.capabilities.Node");
                writer.write(" relationship: tosca.relationships.HostedOn");
                writer.write("occurrences: [ 1, 1 ]");
                writer.write("interfaces:\n Standard:");
                writer.write("type: tosca.interfaces.node.lifecycle.Standard\noperations:");
                writer.write("stop:\ndescription: The standard stop operation");
                writer.write("start:\ndescription: The standard start operation");
                writer.write("create:\ndescription: The standard create operation");
                writer.write("configure:\ndescription: The standard configure operation");
                writer.write("delete:\ndescription: The standard delete operation");
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

            node.type = component.getType().getName();

            if(typeCount.containsKey(node.type)) {
                count = typeCount.get(node.type) + 1;
                typeCount.replace(node.type, count);
            } else {
                typeCount.put(node.type, count);
            }

            node.name = component.getType().getName() + "_" + count;

            node.x = layout.get(node.name)[0];
            node.y = layout.get(node.name)[1];
            node.displayName = component.getName();
            node.properties = component.getProperties();

            for(Relation relation : relations) {
                Requirement requirement = new Requirement();
                if(relation.getSource().equals(node.displayName)) {
                    requirement.type = relation.getType().getName();
                    requirement.node = relation.getTarget().getName(); // Target node displayName
                    requirement.relationship = relation.getName();
                    requirement.capability = "feature";
                    node.requirements.add(requirement);
                }
            }

            nodes.put(node.displayName, node);
        }

        try (FileWriter writer = new FileWriter("/var/repository/servicetemplates/ust.tad.servicetemplates/" + id.toString() + "/ServiceTemplate.tosca")) {
            writer.write("tosca_definitions_version: tosca_simple_yaml_1_3\n\nmetadata:");
            writer.write("targetNamespace: \"ust.tad.servicetemplates\"");
            writer.write("name: " + id.toString());
            writer.write("topology_template:\nnode_templates:");
            for (Node node : nodes.values()) {
                writer.write(node.name + ":");
                writer.write("type: ust.tad.nodetypes" + node.type);
                writer.write("metadata:");
                writer.write("x: " + node.x);
                writer.write("y: " + node.y);
                writer.write("displayName: " + node.displayName);
                writer.write("properties:");
                for (Property property : node.properties) {
                    writer.write(property.getKey() + ": " + property.getValue());
                }
                writer.write("requirements:");
                for (Requirement requirement : node.requirements) {
                    if (requirement.type.equals("HostedOn")) {
                        writer.write("- host:");
                    } else if (requirement.type.equals("ConnectsTo")) {
                        writer.write("- connect:");
                    }
                    writer.write("node: " + nodes.get(requirement.node).name);
                    writer.write("relationship: " + requirement.relationship);
                    writer.write("capability: " + requirement.capability);
                }
            }
            writer.write("relationship_templates:");
            for (Relation relation : relations) {
                writer.write(relation.getName() + ":");
                writer.write("type: tosca.relationships." + relation.getType().getName());
            }
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
