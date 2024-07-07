package ust.tad.layoutpipeline.analysistask;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ust.tad.layoutpipeline.models.tsdm.TechnologySpecificDeploymentModel;

@Service
public class AnalysisTaskResponseSender {

    private static final Logger LOG =
            LoggerFactory.getLogger(AnalysisTaskResponseSender.class);

    @Autowired
    private RabbitTemplate template;

    @Value("${messaging.analysistask.response.exchange.name}")
    private String responseExchangeName;

    public void sendSuccessResponse(UUID taskId)  {}

    public void sendFailureResponse(UUID taskId, String errorMessage)  {}

    public void sendEmbeddedDeploymentModelAnalysisRequest(/*EmbeddedDeploymentModelAnalysisRequest request*/)  {}

    public void sendEmbeddedDeploymentModelAnalysisRequestFromModel(TechnologySpecificDeploymentModel embeddedDeploymentModel, UUID parentTaskId)  {}
}
