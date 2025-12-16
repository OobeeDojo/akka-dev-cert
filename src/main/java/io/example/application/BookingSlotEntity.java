package io.example.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.example.domain.BookingEvent;
import io.example.domain.Participant;
import io.example.domain.Timeslot;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "booking-slot")
public class BookingSlotEntity extends EventSourcedEntity<Timeslot, BookingEvent> {

    private final String entityId;
    private static final Logger logger = LoggerFactory.getLogger(BookingSlotEntity.class);

    public BookingSlotEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    public Effect<Done> markSlotAvailable(Command.MarkSlotAvailable cmd) {
        logger.info("Marking slot available");

        if (currentState() == null) {
            return effects().error("Timeslot does not exist.");
        }

        var participant = cmd.participant();
        var event = new BookingEvent.ParticipantMarkedAvailable(
                commandContext().entityId(),
                participant.id(),
                participant.participantType());

        return effects()
                .persist(event)
                .thenReply(newState -> Done.getInstance());


    }

    public Effect<Done> unmarkSlotAvailable(Command.UnmarkSlotAvailable cmd) {
        return effects().error("not yet implemented");
    }

    // NOTE: booking a slot should produce 3
    // `ParticipantBooked` events
    public Effect<Done> bookSlot(Command.BookReservation cmd) {
        return effects().error("not yet implemented");
    }

    // NOTE: canceling a booking should produce 3
    // `ParticipantCanceled` events
    public Effect<Done> cancelBooking(String bookingId) {
        return effects().error("not yet implemented");

    }

    public ReadOnlyEffect<Timeslot> getSlot() {
        return effects().error("not yet implemented");
    }

    @Override
    public Timeslot emptyState() {
        return new Timeslot(
                // NOTE: these are just estimates for capacity based on it being a sample
                HashSet.newHashSet(10), HashSet.newHashSet(10));
    }

    @Override
    public Timeslot applyEvent(BookingEvent event) {
        // Supply your own implementation to update state based
        // on the event
        switch (event) {
            case BookingEvent.ParticipantMarkedAvailable evt ->
                currentState().reserve(new BookingEvent.ParticipantMarkedAvailable(
                        evt.slotId(),
                        evt.participantId(),
                        evt.participantType()));
            case BookingEvent.ParticipantUnmarkedAvailable evt ->
                currentState().unreserve(new BookingEvent.ParticipantUnmarkedAvailable(
                        evt.slotId(),
                        evt.participantId(),
                        evt.participantType()
                ));
            case BookingEvent.ParticipantBooked evt ->
                currentState().book(new BookingEvent.ParticipantBooked(
                        evt.slotId(),
                        evt.participantId(),
                        evt.participantType(),
                        evt.bookingId()
                ));
            case BookingEvent.ParticipantCanceled evt ->
                currentState().cancelBooking(evt.bookingId());
        }
        return currentState();
    }

    public sealed interface Command {
        record MarkSlotAvailable(Participant participant) implements Command {
        }

        record UnmarkSlotAvailable(Participant participant) implements Command {
        }

        record BookReservation(
                String studentId, String aircraftId, String instructorId, String bookingId)
                implements Command {
        }
    }
}
