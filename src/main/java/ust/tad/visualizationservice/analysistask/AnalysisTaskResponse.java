package ust.tad.visualizationservice.analysistask;

import java.util.Objects;
import java.util.UUID;

public class AnalysisTaskResponse {

  private UUID taskId;

  private boolean success;

  private String errorMessage;

  public AnalysisTaskResponse() {}

  /**
   * Constructor for a successful response.
   *
   * @param taskId The ID of the task.
   * @param success Whether the task was successful.
   * @param errorMessage The error message.
   */
  public AnalysisTaskResponse(UUID taskId, boolean success, String errorMessage) {
    this.taskId = taskId;
    this.success = success;
    this.errorMessage = errorMessage;
  }

  public UUID getTaskId() {
    return this.taskId;
  }

  public void setTaskId(UUID taskId) {
    this.taskId = taskId;
  }

  public boolean isSuccess() {
    return this.success;
  }

  public boolean getSuccess() {
    return this.success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public String getErrorMessage() {
    return this.errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public AnalysisTaskResponse taskId(UUID taskId) {
    setTaskId(taskId);
    return this;
  }

  public AnalysisTaskResponse success(boolean success) {
    setSuccess(success);
    return this;
  }

  public AnalysisTaskResponse errorMessage(String errorMessage) {
    setErrorMessage(errorMessage);
    return this;
  }

  /**
   * Compares this object to another object.
   *
   * @param o The object to compare to.
   * @return {@code true} if the objects are equal, {@code false} otherwise.
   */
  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof AnalysisTaskResponse)) {
      return false;
    }
    AnalysisTaskResponse analysisTaskResponse = (AnalysisTaskResponse) o;
    return Objects.equals(taskId, analysisTaskResponse.taskId)
        && success == analysisTaskResponse.success
        && Objects.equals(errorMessage, analysisTaskResponse.errorMessage);
  }

  /**
   * Generates a hash code for this object.
   *
   * @return The hash code.
   */
  @Override
  public int hashCode() {
    return Objects.hash(taskId, success, errorMessage);
  }

  /**
   * Generates a string representation of this object.
   *
   * @return The string representation.
   */
  @Override
  public String toString() {
    return "{"
        + " taskId='"
        + getTaskId()
        + "'"
        + ", success='"
        + isSuccess()
        + "'"
        + ", errorMessage='"
        + getErrorMessage()
        + "'"
        + "}";
  }
}
