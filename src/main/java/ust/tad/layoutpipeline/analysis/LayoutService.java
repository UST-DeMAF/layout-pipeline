package ust.tad.layoutpipeline.analysis;

import ust.tad.layoutpipeline.models.tadm.Component;
import ust.tad.layoutpipeline.models.tadm.Relation;
import ust.tad.layoutpipeline.models.tadm.TechnologyAgnosticDeploymentModel;

import java.io.*;
import java.util.*;

public class LayoutService {
    private List<Component> components;
    private List<Relation>  relations;
    private UUID transformationProcessId;

    private String dotPath;

    private Map<String, float[]> layout = new HashMap<>();

    void generateLayout(TechnologyAgnosticDeploymentModel tadm) {
        components = tadm.getComponents();
        relations = tadm.getRelations();
        transformationProcessId = tadm.getTransformationProcessId();

        dotPath = "/app/target/data/inputs/" + transformationProcessId.toString() +".dot";

        createDotFile(components, relations, dotPath);

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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
