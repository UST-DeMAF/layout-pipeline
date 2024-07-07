package ust.tad.layoutpipeline.models.tsdm;

public class InvalidNumberOfLinesException extends Exception{
    public InvalidNumberOfLinesException(String errorMessage) {
        super(errorMessage);
    }
}