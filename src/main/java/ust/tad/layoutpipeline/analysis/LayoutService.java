package ust.tad.layoutpipeline.analysis;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ust.tad.layoutpipeline.models.tadm.*;
import ust.tad.layoutpipeline.registration.PluginRegistrationRunner;

@Service
public class LayoutService {

  private static final Logger LOG = LoggerFactory.getLogger(PluginRegistrationRunner.class);

  private List<Component> components = new ArrayList<>();
  private List<ComponentType> componentTypes = new ArrayList<>();
  private List<Relation> relations = new ArrayList<>();

  private Map<String, float[]> layout = new HashMap<>();

  /*
   * Generates the layout of the components and relations in the TADM.
   * @param tadm The TechnologyAgnosticDeploymentModel to generate the layout for.
   */
  public void generateLayout(TechnologyAgnosticDeploymentModel tadm) {
    components = tadm.getComponents();
    componentTypes = tadm.getComponentTypes();
    relations = tadm.getRelations();
    UUID transformationProcessId = tadm.getTransformationProcessId();

    String path = "/var/repository/graphviz/";
    String file = transformationProcessId.toString() + ".dot";

    try {
      Files.createDirectories(Paths.get(path));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    createDotFile(components, relations, path + file);
    layout = callGraphVIZ(path + file);

    createNodeTypes(componentTypes, transformationProcessId);
    createServiceTemplate(components, relations, transformationProcessId);
    clearVariables();
  }

  /*
   * Creates a .dot file from the components and relations in the TADM.
   * @param components The components in the TADM.
   * @param relations The relations in the TADM.
   * @param path The path to save the .dot file to.
   */
  private void createDotFile(List<Component> components, List<Relation> relations, String path) {
    List<String> nodes = new ArrayList<>();
    List<String> graph = new ArrayList<>();
    List<String> subgraph = new ArrayList<>();

    for (Component component : components) {
      nodes.add("\"" + component.getName() + "\" [shape=\"polygon\" width=2.5 height=0.8]");
    }

    for (Relation relation : relations) {
      if (relation.getType().getName().equals("HostedOn")) {
        graph.add(
            "\""
                + relation.getSource().getName()
                + "\" -> \""
                + relation.getTarget().getName()
                + "\" [label=\"HostedOn\"]");
      } else if (relation.getType().getName().equals("ConnectsTo")) {
        subgraph.add(
            "\""
                + relation.getSource().getName()
                + "\" -> \""
                + relation.getTarget().getName()
                + "\" [label=\"ConnectsTo\" style=\"dashed\"]");
      }
    }

    try (FileWriter writer = new FileWriter(path)) {
      writer.write("strict digraph {");
      for (String node : nodes) {
        writer.write(node);
      }
      for (String relation : graph) {
        writer.write(relation);
      }
      writer.write("subgraph {");
      for (String relation : subgraph) {
        writer.write(relation);
      }
      writer.write("}}");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /*
   * Calls the GraphVIZ tool to generate the layout of the components and relations in the TADM.
   * @param path The path to the .dot file to generate the layout for.
   * @return A map of the component names and their coordinates in the layout.
   */
  private Map<String, float[]> callGraphVIZ(String path) {
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
          maxY = Math.max(coords[1], maxY);
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

  /*
   * Creates the node types for the components in the TADM.
   * @param componentTypes The component types in the TADM.
   * @param id The ID of the transformation process.
   */
  private void createNodeTypes(List<ComponentType> componentTypes, UUID id) {
    for (ComponentType componentType : componentTypes) {
      String nodeTypesPath =
          "/var/repository/nodetypes/"
              + id.toString()
              + ".ust.tad.nodetypes/"
              + componentType.getName()
              + "/";

      try {
        Files.createDirectories(Paths.get(nodeTypesPath));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      try (FileWriter writer = new FileWriter(nodeTypesPath + "NodeType.tosca")) {
        writer.write("tosca_definitions_version: tosca_simple_yaml_1_3\n\n");
        writer.write("node_types:\n");
        writer.write("  " + id + ".ust.tad.nodetypes." + componentType.getName() + ":\n");
        writer.write("    derived_from: tosca.nodes.Root\n");
        writer.write("    metadata:\n");
        writer.write("      targetNamespace: " + id + ".ust.tad.nodetypes\n");
        writer.write("      abstract: \"false\"\n");
        writer.write("      final: \"false\"\n");
        writer.write("    properties:\n");
        List<Property> properties = componentType.getProperties();
        for (Property property : properties) {
          writer.write("      " + property.getKey() + ":\n");
          writer.write("        type: " + property.getType().name() + "\n");
          writer.write("        required: " + property.getRequired() + "\n");
          writer.write("        default: " + property.getValue().toString() + "\n");
        }
        writer.write("    requirements:\n");
        writer.write("      - host:\n");
        writer.write("          capability: tosca.capabilities.Node\n");
        writer.write("          relationship: tosca.relationships.HostedOn\n");
        writer.write("          occurrences: [ 1, 1 ]\n");
        writer.write("    interfaces:\n");
        writer.write("      Standard:\n");
        writer.write("        type: tosca.interfaces.node.lifecycle.Standard\n");
        writer.write("        operations:\n");
        writer.write("          stop:\n");
        writer.write("            description: The standard stop operation\n");
        writer.write("          start:\n");
        writer.write("            description: The standard start operation\n");
        writer.write("          create:\n");
        writer.write("            description: The standard create operation\n");
        writer.write("          configure:\n");
        writer.write("            description: The standard configure operation\n");
        writer.write("          delete:\n");
        writer.write("            description: The standard delete operation\n");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /*
   * Creates the service template for the components and relations in the TADM.
   * @param components The components in the TADM.
   * @param relations The relations in the TADM.
   * @param id The ID of the transformation process.
   */
  private void createServiceTemplate(
      List<Component> components, List<Relation> relations, UUID id) {
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

      if (typeCount.containsKey(node.type)) {
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
      for (Relation relation : relations) {
        Requirement requirement = new Requirement();
        if (relation.getSource().getName().equals(node.displayName)) {
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

    String serviceTemplatePath =
        "/var/repository/servicetemplates/ust.tad.servicetemplates/" + id.toString() + "/";

    try {
      Files.createDirectories(Paths.get(serviceTemplatePath));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    try (FileWriter writer = new FileWriter(serviceTemplatePath + "ServiceTemplate.tosca")) {
      writer.write("tosca_definitions_version: tosca_simple_yaml_1_3\n\n");
      writer.write("metadata:\n");
      writer.write("  targetNamespace: \"ust.tad.servicetemplates\"\n");
      writer.write("  name: " + id + "\n");
      writer.write("topology_template:\n");
      writer.write("  node_templates:\n");
      for (Node node : nodes.values()) {
        writer.write("    " + node.name + ":\n");
        if (node.type == null) {
          writer.write("      type: tosca.nodes.Root\n");
        } else {
          writer.write("      type: " + id + ".ust.tad.nodetypes." + node.type + "\n");
        }
        writer.write("      metadata:\n");
        writer.write("        x: '" + node.x + "'\n");
        writer.write("        y: '" + node.y + "'\n");
        writer.write("        displayName: " + node.displayName + "\n");
        writer.write("      properties:\n");
        for (Property property : node.properties) {
          if (isNumeric(property.getKey())) {
            writer.write("        \"" + property.getKey() + "\": " + property.getValue() + "\n");
          } else {
            writer.write("        " + property.getKey() + ": " + property.getValue() + "\n");
          }
        }
        if (!node.requirements.isEmpty()) {
          writer.write("      requirements:\n");
          for (Requirement requirement : node.requirements) {
            if (requirement.type.equals("HostedOn")) {
              writer.write("        - host:\n");
            } else if (requirement.type.equals("ConnectsTo")) {
              writer.write("        - connect:\n");
            }
            writer.write("            node: " + nodes.get(requirement.node).name + "\n");
            writer.write("            relationship: " + requirement.relationship + "\n");
            writer.write("            capability: " + requirement.capability + "\n");
          }
        }
      }
      writer.write("  relationship_templates: \n");
      for (Relation relation : relations) {
        writer.write("    " + relation.getName() + ":\n");
        writer.write("      type: tosca.relationships." + relation.getType().getName() + "\n");
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /*
   * Clears the variables used to generate the layout.
   */
  private void clearVariables() {
    components.clear();
    componentTypes.clear();
    relations.clear();
    layout.clear();
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

  private static class Node {
    String name;
    String type;
    float x;
    float y;
    String displayName;
    List<Property> properties;
    List<Requirement> requirements;
  }

  private static class Requirement {
    String type;
    String node;
    String relationship;
    String capability;
  }
}
