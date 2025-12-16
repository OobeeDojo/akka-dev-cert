package io.example.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.example.domain.BookingEvent;
import io.example.domain.Participant;
import io.example.domain.Timeslot;

import java.util.*;

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
        logger.info("Unmarking slot available");

        if (currentState() == null) {
            return effects().error("Timeslot does not exist.");
        }

        var participant = cmd.participant();
        var event = new BookingEvent.ParticipantUnmarkedAvailable(
                commandContext().entityId(),
                participant.id(),
                participant.participantType());

        return effects()
                .persist(event)
                .thenReply(newState -> Done.getInstance());

    }

    // NOTE: booking a slot should produce 3
    // `ParticipantBooked` events
    public Effect<Done> bookSlot(Command.BookReservation cmd) {

        if (currentState() == null) {
            return effects().error("Timeslot does not exist.");
        }

        String studentId = cmd.studentId();
        String aircraftId = cmd.aircraftId();
        String instructorId = cmd.instructorId();
        String bookingId = cmd.bookingId();

        if (currentState().isBookable(studentId, aircraftId, instructorId)) {

            var student_booking_event = new BookingEvent.ParticipantBooked(
                    commandContext().entityId(),
                    studentId,
                    Participant.ParticipantType.STUDENT,
                    bookingId
            );

            var aircraft_booking_event = new BookingEvent.ParticipantBooked(
                    commandContext().entityId(),
                    aircraftId,
                    Participant.ParticipantType.AIRCRAFT,
                    bookingId
            );

            var instructor_booking_event = new BookingEvent.ParticipantBooked(
                    commandContext().entityId(),
                    instructorId,
                    Participant.ParticipantType.INSTRUCTOR,
                    bookingId
            );

            return effects()
                    .persist(student_booking_event,
                            aircraft_booking_event,
                            instructor_booking_event)
                    .thenReply(newState -> Done.getInstance());

        }

        return effects().error("Timeslot is not bookable.");

    }

    // NOTE: canceling a booking should produce 3
    // `ParticipantCanceled` events
    public Effect<Done> cancelBooking(String bookingId) {

        if (currentState() == null) {
            return effects().error("Timeslot does not exist.");
        }

        var bookingList = currentState().findBooking(bookingId);

        if (bookingList.isEmpty()) {
            return effects().error("Booking does not exist.");
        }

        List<BookingEvent> eventsToPersist = new ArrayList<>();

        for (Timeslot.Booking booking : bookingList) {
            eventsToPersist.add(new BookingEvent.ParticipantCanceled(
                    commandContext().entityId(),
                    booking.participant().id(),
                    booking.participant().participantType(),
                    bookingId
            ));
        }

        try {
        return effects()
                .persist(eventsToPersist.get(0),eventsToPersist.get(1),eventsToPersist.get(2))
                .thenReply(newState -> Done.getInstance());
        } catch (NoSuchElementException e) {
            return effects().error("Only 3 participants found in booking.");
        }


    }

    public ReadOnlyEffect<Timeslot> getSlot() {
        return effects().reply(currentState());
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
