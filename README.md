# visualization-service
The visualization-service is one of many plugins of the [DeMAF](https://github.com/UST-DeMAF) project.
It was developed to layout [EDMM Models](https://github.com/UST-EDMM) automatically with the help of [GraphVIZ](https://gitlab.com/graphviz/graphviz) and to visualize them with [Eclipse Winery](https://github.com/winery/winery/).

The plugin only works (without adaptions) in the context of the entire DeMAF application using the [deployment-config](https://github.com/UST-DeMAF/deployment-config).
The documentation for setting up the entire DeMAF application locally is [here](https://github.com/UST-DeMAF/EnPro-Documentation).

## Build and Run Application

You can run the application without the [deployment-config](https://github.com/UST-DeMAF/deployment-config), but it will not run as it needs to register itself at the [analysis-manager](https://github.com/UST-DeMAF/analysis-manager).

If you want to boot it locally nevertheless use the following commands.

```shell
mvn clean package
java -jar target/visualization-service-0.2.0-SNAPSHOT.jar
```

## Usage of Application
To visualize an EDMM model, follow steps 1-6 from [here](https://github.com/UST-DeMAF/EnPro-Documentation?tab=readme-ov-file#getting-started).
Then enter the following command in the DeMAF-shell:

```shell
transform --location file:/usr/share/filename.yaml --technology visualization-service --options dpi=96,flatten=partial,width=1920,height=1080,visualize=true
```

The plugin supports the following flags:
- `visualize=true/false` *(default true)*
- `height=pixel` *(default 1920)*
- `width=pixel` *(default 1080)*
- `flatten=true/false/partial` *(default false)*
- `dpi=pixels per inch of your display` *(default 96 dpi)*

The options arguments can also be specified in other transformations; these are then passed on to the visualization-service for automatic visualization. 

## Visualization Service-Specific Configurations
This plugin has a few specialties compared to other DeMAF plugins:
1. Automatic execution: As long as the option `visualize=false` is not specified during the transformation, this plugin is automatically called to visualize the model after a successful transformation into an EDMM model.
2. No directories: Since EDMM models are typically defined in a single YAML file, no directories are supported as a location.

## Debugging
When running the project locally using e.g. IntelliJ IDEA or from the command-line, make sure that the plugin is not also running
in a Docker container, launched by the deployment-config, otherwise the port is blocked.