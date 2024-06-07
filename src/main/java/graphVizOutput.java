import java.io.*;
import java.util.HashMap;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;

public class graphVizOutput {

    public static void main(String[] args) {

        cmdCommand();
        updateYaml();

    }

    /*
    Creates a HashMap containing the "real" x and y coords for the yaml/tosca file.
     */
    public static Map<String, float[]> createHashMap() {
        String filePath = "output.txt";

        Map<String, float[]> output = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("node")) {
                    String[] splits = line.split(" ");
                    String id = splits[1];
                    float[] coords = {Float.parseFloat(splits[2]), Float.parseFloat(splits[3])};
                    output.put(id, coords);
                }

            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (Map.Entry<String, float[]> entry : output.entrySet()) {
            System.out.printf("ID: %s X: %f Y: %f \n", entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
        }

        return output;
    }


    public static void cmdCommand() {

        // The command to be executed
        String command = "dot -Tplain example.dot"; // Replace with your actual command

        // The file to save the output
        String outputFilePath = "output.txt";

        ProcessBuilder processBuilder = new ProcessBuilder();

        //THIS NEEDS TO CHANGES DEPENDEND which OS you use!
        // Command for Windows
        //processBuilder.command("cmd.exe", "/c", command);
        //THIS NEEDS TO CHANGES DEPENDEND which OS you use!
        // If on Unix-based systems, use:
        processBuilder.command("sh", "-c", command);
        processBuilder.directory(new File("/mnt/d/GitHub/st149535/layout-pipeline"));

        try {
            // Start the process
            Process process = processBuilder.start();

            // Get the input stream of the process (i.e., the output of the command)
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            // Create a FileWriter to write the output to a file
            FileWriter fileWriter = new FileWriter(outputFilePath);

            String line;
            while ((line = reader.readLine()) != null) {
                fileWriter.write(line + System.lineSeparator());
            }

            // Close the file writer
            fileWriter.close();

            // Wait for the process to complete
            int exitCode = process.waitFor();
            System.out.println("Exited with code: " + exitCode);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void updateYaml() {
        try {
            // Load the YAML file
            Yaml yaml = new Yaml();
            FileInputStream inputStream = new FileInputStream("example.tosca");
            Map<String, Object> data = yaml.load(inputStream);

            // Define a mapping of node template keys to new x and y values

            Map<String, float[]> newValues = createHashMap();
            float maxY = 0;

            for (Map.Entry<String, float[]> entry : newValues.entrySet()) {
                float[] newXY = entry.getValue();

                if (newXY[1] >= maxY) {
                    maxY = newXY[1];
                }
            }
            // Add more mappings as needed...

            // Update x and y values in node templates based on the mapping
            Map<String, Object> topologyTemplate = (Map<String, Object>) data.get("topology_template");
            Map<String, Object> nodeTemplates = (Map<String, Object>) topologyTemplate.get("node_templates");

            for (Map.Entry<String, Object> entry : nodeTemplates.entrySet()) {
                String nodeKey = entry.getKey();
                Map<String, Object> nodeTemplate = (Map<String, Object>) entry.getValue();
                Map<String, Object> metadata = (Map<String, Object>) nodeTemplate.get("metadata");

                if (newValues.containsKey(nodeKey)) {
                    float[] newXY = newValues.get(nodeKey);

                    metadata.put("x", String.valueOf(Math.round(newXY[0] * 100)));
                    metadata.put("y", String.valueOf(Math.round(Math.abs(newXY[1] - maxY) * 100 + 100)));
                }
            }

            // Save the updated data back to the YAML file
            Representer representer = new Representer();
            representer.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            Yaml yamlOutput = new Yaml(representer);
            FileWriter writer = new FileWriter("output.tosca");
            yamlOutput.dump(data, writer);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

    


