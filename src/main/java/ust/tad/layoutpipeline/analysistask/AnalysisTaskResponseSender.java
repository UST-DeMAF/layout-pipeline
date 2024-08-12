package ust.tad.layoutpipeline.analysistask;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ust.tad.layoutpipeline.models.tsdm.DeploymentModelContent;
import ust.tad.layoutpipeline.models.tsdm.Line;
import ust.tad.layoutpipeline.models.tsdm.TechnologySpecificDeploymentModel;

@Service
public class AnalysisTaskResponseSender {

  private static final Logger LOG = LoggerFactory.getLogger(AnalysisTaskResponseSender.class);

  @Autowired private RabbitTemplate template;

  @Value("${messaging.analysistask.response.exchange.name}")
  private String responseExchangeName;

  /**
   * Sends a success response to the response exchange.
   *
   * @param taskId The ID of the task.
   */
  public void sendSuccessResponse(UUID taskId) {
    LOG.info("Transformation completed successfully, sending success response");
    ObjectMapper objectMapper = new ObjectMapper();
    AnalysisTaskResponse analysisTaskResponse = new AnalysisTaskResponse();
    analysisTaskResponse.setTaskId(taskId);
    analysisTaskResponse.setSuccess(true);

    Message message;
    // Convert the AnalysisTaskResponse object to a JSON string and send it as a message
    try {
      message =
          MessageBuilder.withBody(objectMapper.writeValueAsString(analysisTaskResponse).getBytes())
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .setHeader("formatIndicator", "AnalysisTaskResponse")
              .build();
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      return;
    }
    template.convertAndSend(responseExchangeName, "", message);
  }

  /**
   * Sends a failure response to the response exchange. If the task ID is not null, it is also
   * included in the response.
   *
   * @param taskId The ID of the task.
   * @param errorMessage The error message.
   */
  public void sendFailureResponse(UUID taskId, String errorMessage) {
    LOG.info("Sending failure response: " + errorMessage);
    ObjectMapper objectMapper = new ObjectMapper();
    AnalysisTaskResponse analysisTaskResponse = new AnalysisTaskResponse();
    if (taskId != null) {
      analysisTaskResponse.setTaskId(taskId);
    }
    analysisTaskResponse.setSuccess(false);
    analysisTaskResponse.setErrorMessage(errorMessage);

    Message message;
    // Convert the AnalysisTaskResponse object to a JSON string and send it as a message
    try {
      message =
          MessageBuilder.withBody(objectMapper.writeValueAsString(analysisTaskResponse).getBytes())
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .setHeader("formatIndicator", "AnalysisTaskResponse")
              .build();
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      return;
    }
    template.convertAndSend(responseExchangeName, "", message);
  }

  /**
   * Sends an EmbeddedDeploymentModelAnalysisRequest to the response exchange. The request contains
   * the transformation process ID, the technology, the commands and the locations. The locations
   * contain the URL and the start and end line numbers of the content.
   *
   * @param request The EmbeddedDeploymentModelAnalysisRequest to be sent.
   */
  public void sendEmbeddedDeploymentModelAnalysisRequest(
      EmbeddedDeploymentModelAnalysisRequest request) {
    LOG.info("Sending EmbeddedDeploymentModelAnalysisRequest: " + request.toString());
    ObjectMapper objectMapper = new ObjectMapper();

    Message message;
    // Convert the EmbeddedDeploymentModelAnalysisRequest object to a JSON string and send it as a
    // message
    try {
      message =
          MessageBuilder.withBody(objectMapper.writeValueAsString(request).getBytes())
              .setContentType(MessageProperties.CONTENT_TYPE_JSON)
              .setHeader("formatIndicator", "EmbeddedDeploymentModelAnalysisRequest")
              .build();
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      return;
    }
    template.convertAndSend(responseExchangeName, "", message);
  }

  /**
   * Sends an EmbeddedDeploymentModelAnalysisRequest to the response exchange. The request contains
   * the transformation process ID, the technology, the commands and the locations. The locations
   * contain the URL and the start and end line numbers of the content.
   *
   * @param embeddedDeploymentModel The embedded deployment model to be analyzed.
   * @param parentTaskId The ID of the parent task.
   */
  public void sendEmbeddedDeploymentModelAnalysisRequestFromModel(
      TechnologySpecificDeploymentModel embeddedDeploymentModel, UUID parentTaskId) {
    EmbeddedDeploymentModelAnalysisRequest request = new EmbeddedDeploymentModelAnalysisRequest();
    request.setParentTaskId(parentTaskId);
    request.setTransformationProcessId(embeddedDeploymentModel.getTransformationProcessId());
    request.setTechnology(embeddedDeploymentModel.getTechnology());
    request.setCommands(embeddedDeploymentModel.getCommands());
    List<Location> locations = new ArrayList<>();

    // Create a location for each content element
    for (DeploymentModelContent deploymentModelContent : embeddedDeploymentModel.getContent()) {
      Location location = new Location();
      location.setUrl(deploymentModelContent.getLocation());
      int startLineNumber = 0;
      int endLineNumber = 0;
      for (Line line : deploymentModelContent.getLines()) {
        if (line.getNumber() < startLineNumber) {
          startLineNumber = line.getNumber();
        } else if (line.getNumber() > endLineNumber) {
          endLineNumber = line.getNumber();
        }
      }
      location.setStartLineNumber(startLineNumber);
      location.setEndLineNumber(endLineNumber);
      locations.add(location);
    }
    request.setLocations(locations);

    sendEmbeddedDeploymentModelAnalysisRequest(request);
  }
}
