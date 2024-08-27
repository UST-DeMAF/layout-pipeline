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

@Service
public class LayoutService {

  private static final Logger LOG = LoggerFactory.getLogger(LayoutService.class);

  private List<Component> components = new ArrayList<>();
  private List<ComponentType> componentTypes = new ArrayList<>();
  private List<Relation> relations = new ArrayList<>();

  private final Map<String, int[]> layout = new HashMap<>();

  private double dpi;
  private String flatten;
  private double[] graphSize;
  private double[] nodeSize;

  /*
   * Generates the layout of the components and relations in the TADM.
   * @param tadm The TechnologyAgnosticDeploymentModel to generate the layout for.
   */
  public void generateLayout(TechnologyAgnosticDeploymentModel tadm, double dpi, String flatten, int width, int height) {
    components = tadm.getComponents();
    componentTypes = tadm.getComponentTypes();
    relations = tadm.getRelations();
    UUID transformationProcessId = tadm.getTransformationProcessId();

    this.dpi = dpi;
    this.flatten = flatten;
    graphSize = new double[]{convertPixelsToInches(width * 0.82), convertPixelsToInches(height * 0.79)};
    nodeSize = new double[]{convertPixelsToInches(225), convertPixelsToInches(60)};

    String path = "/var/repository/graphviz/";
    String file = transformationProcessId.toString() + ".dot";

    try {
      Files.createDirectories(Paths.get(path));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    createDotFile(components, relations, path + file);
    callGraphVIZ(path + file);

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
    List<String> rankSame = new ArrayList<>();

    Map<String, List<String>> graph = new HashMap<>();
    Map<String, List<String>> subgraph = new HashMap<>();

    for (Component component : components) {
      nodes.add("    \"" + component.getName() + "\"\n");
    }

    for (Relation relation : relations) {
      String relationType = relation.getType().getName();
      String source = relation.getSource().getName();
      String target = relation.getTarget().getName();

      if (relationType.equals("HostedOn")) {
        if (graph.containsKey(source)) {
          graph.get(source).add(target);
        } else {
          List<String> targets = new ArrayList<>();
          targets.add(target);
          graph.put(source, targets);
        }
      } else if (relationType.equals("ConnectsTo")) {
        if (subgraph.containsKey(source)) {
          subgraph.get(source).add(target);
          if (!rankSame.contains(source)) {
            rankSame.add(source);
          }
        } else {
          List<String> targets = new ArrayList<>();
          targets.add(target);
          subgraph.put(source, targets);
        }
      }
    }

    try (FileWriter writer = new FileWriter(path)) {
      writer.write("strict digraph {\n");
      if (flatten.equals("true")) {
        writer.write("    graph [dpi=" + dpi + ", rank=\"same\", ratio=\"compress\", size=\"" + graphSize[0] + "," + graphSize[1] + "\", splines=\"ortho\"]\n");
      } else {
        writer.write("    graph [dpi=" + dpi + ", ratio=\"compress\", size=\"" + graphSize[0] + "," + graphSize[1] + "\", splines=\"ortho\"]\n");
      }
      writer.write("    node [fixedsize=\"true\", shape=\"polygon\",  width=" + nodeSize[0] +", height=" + nodeSize[1] + "]\n");
      writer.write("    edge [label=\"HostedOn\", style=\"solid\"]\n");
      for (String node : nodes) {
        writer.write(node);
      }
      for (Map.Entry<String, List<String>> entry : graph.entrySet()) {
        String source = entry.getKey();
        List<String> targets = entry.getValue();
        if (targets.size() > 1) {
          writer.write("    \"" + source + "\" -> { ");
          for (String target : targets) {
            writer.write("\"" + target + "\" ");
          }
          writer.write("} [weight=2]\n");
        } else {
          writer.write("    \"" + source + "\" -> \"" + targets.get(0) + "\"\n");
        }
      }
      writer.write("    subgraph {\n");
      writer.write("        edge [label=\"ConnectsTo\", style=\"dashed\"]\n");
      if (flatten.equals("partial")) {
        writer.write("        { rank=\"same\" ");
        for (String node : rankSame) {
          writer.write("\"" + node + "\" ");
        }
        writer.write("}\n");
      }
      for (Map.Entry<String, List<String>> entry : subgraph.entrySet()) {
        String source = entry.getKey();
        List<String> targets = entry.getValue();
        if (targets.size() > 1) {
          writer.write("        \"" + source + "\" -> { ");
          for (String target : targets) {
            writer.write("\"" + target + "\" ");
          }
          writer.write("} [weight=2]\n");
        } else {
          writer.write("        \"" + source + "\" -> \"" + targets.get(0) + "\"\n");
        }
      }
      writer.write("    }\n}");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /*
   * Calls the GraphVIZ tool to generate the layout of the components and relations in the TADM.
   * @param path The path to the .dot file to generate the layout for.
   * @return A map of the component names and their coordinates in the layout.
   */
  private void callGraphVIZ(String path) {
    Map<String, double[]> output = new HashMap<>();
    String command = "dot -Tplain " + path;

    ProcessBuilder processBuilder = new ProcessBuilder();
    processBuilder.command("sh", "-c", command);

    try {
      Process process = processBuilder.start();
      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

      double maxY = 0;
      String line;

      while ((line = reader.readLine()) != null) {
        if (line.startsWith("node")) {
          String[] splits = line.split(" ");
          String node = splits[1].replaceAll("\"", "");
          double[] coords = {Double.parseDouble(splits[2]), Double.parseDouble(splits[3])};
          maxY = Math.max(coords[1], maxY);
          output.put(node, coords);
        }
      }

      for (Map.Entry<String, double[]> entry : output.entrySet()) {
        String node = entry.getKey();
        double[] coords = entry.getValue();
        int x = convertInchesToPixels(coords[0]);
        int y = convertInchesToPixels(Math.abs(coords[1] - maxY)) + 100;
        layout.put(node, new int[]{x, y});
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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
          String key = property.getKey();
          if (isNumeric(key)) {
            writer.write("      \"" + key + "\":\n");
          } else {
            writer.write("      " + key + ":\n");
          }
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
          LOG.info("Component type of the component {} is not defined.", component.getName());
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

      int[] coords = layout.get(node.displayName);
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
          String key = property.getKey();
          if (isNumeric(key)) {
            writer.write("        \"" + key + "\": " + property.getValue() + "\n");
          } else {
            writer.write("        " + key + ": " + property.getValue() + "\n");
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
  * Converts inches to pixels.
  * @param inches the inches
  * @return the pixels
  */
  private int convertInchesToPixels(double inches) {
    return (int) Math.round(inches * dpi);
  }

  /*
  * Converts pixels to inches.
  * @param pixels the pixels
  * @return the inches
  */
  private double convertPixelsToInches(double pixels) {
    return pixels / dpi;
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
    int x;
    int y;
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
