package ust.tad.layoutpipeline.models.tsdm;

public class InvalidNumberOfContentException extends Exception{
    public InvalidNumberOfContentException(String errorMessage) {
        super(errorMessage);
    }
}