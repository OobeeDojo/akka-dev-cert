package io.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelTimeoutException;
import akka.javasdk.agent.RateLimitException;
import akka.javasdk.agent.ToolCallExecutionException;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;

import java.util.List;

import static java.lang.Integer.parseInt;

/*
 * The flight conditions agent is responsible for making a determination about the flight
 * conditions for a given day and time. You will need to clearly define the success criteria
 * for the report and instruct the agent (in the system prompt) about the schema of
 * the results it must return (the ConditionsReport).
 *
 * Also be sure to provide clear instructions on how and when tools should be invoked
 * in order to generate results.
 *
 * Flight conditions criteria don't need to be exhaustive, but you should supply the
 * criteria so that an agent does not need to make an external HTTP call to query
 * the condition limits.
 */

@Component(id = "flight-conditions-agent")
public class FlightConditionsAgent extends Agent {

    public record ConditionsReport(String timeSlotId, Boolean meetsRequirements) {
    }

    private static final String SYSTEM_MESSAGE = """
            You are an agent responsible for evaluating flight conditions.
            You will receive information about a particular time slot in the following format:
            
            YYYY-MM-DD-HH
            
            e.g. 2025-08-08-09, represents a slot for August 8th, 2025 at 9am local time.
            
            You must retrieve the weather conditions for the associated date and time, and make a final judgement as to whether the conditions are suitable for a flying lesson.
            
            Your expected output format is as follows:
              {
                "timeSlotId": "<String (timeSlotId)>",
                "meetsRequirements": "<Boolean (True/False)>",
              }
            
            
            """.stripIndent();

    public Effect<ConditionsReport> query(String timeSlotId) {
        return effects()
                .systemMessage(SYSTEM_MESSAGE)
                .userMessage("Validate the weather conditions for the associated date and time (YYYY-MM-DD-HH): %s".formatted(timeSlotId))
                .responseAs(ConditionsReport.class)
                .onFailure(exception -> {
                    switch (exception) {
                        case RateLimitException exc -> throw new RateLimitException("Rate limit exceeded, try again later");
                        case ModelTimeoutException exc -> throw new ModelTimeoutException("Request timeout, service is delayed");
                        case ToolCallExecutionException exc -> throw new ToolCallExecutionException("Tool error",exc.getToolName(),exc);
                        default -> throw new RuntimeException("Agent Call Failed", exception);
                    }
                })
                .thenReply();
    }

    /*
     * You can choose to hard code the weather conditions for specific days or you
     * can actually
     * communicate with an external weather API. You should be able to get both
     * suitable weather
     * conditions and poor weather conditions from this tool function for testing.
     */
    //TODO weather service - create bootstrap file
    @FunctionTool(description = "Queries the weather conditions as they are forecasted based on the time slot ID of the training session booking")
    private String getWeatherForecast(String timeSlotId) {
        var month = parseInt(timeSlotId.split("-")[1]);

        return switch (month) {
            // WINTER: Clear "FALSE" due to Icing and low visibility
            case 12, 1, 2 -> "Heavy freezing rain and thick fog. Visibility 1/4 mile. Surface icing present.";

            // SPRING: Clear "FALSE" due to Severe Turbulence/Storms
            case 3, 4 -> "Severe thunderstorms with hail. Sustained winds 40 knots, gusting to 60.";

            // LATE SPRING: Clear "TRUE" - Perfect VFR (Visual Flight Rules)
            case 5, 6 -> "Clear skies. Visibility 10 miles. Wind calm at 2 knots.";

            // SUMMER: Clear "TRUE" - Standard Fair Weather
            case 7, 8 -> "Sunny skies with a few scattered clouds at 10,000 feet. Visibility unlimited.";

            // FALL: Clear "FALSE" due to Low Ceiling (Cloud Base)
            case 9, 10 -> "Solid overcast ceiling at 200 feet. Continuous heavy rain and mist.";

            // LATE FALL: Clear "FALSE" due to Wind Shear
            case 11 -> "Extreme low-level wind shear and turbulence. Gale warning in effect.";

            default -> "Extreme weather emergency. Volcanic ash detected.";
        };
    }
}
