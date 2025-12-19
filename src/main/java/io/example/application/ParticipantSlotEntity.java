package io.example.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import io.example.domain.Participant.ParticipantType;

@Component(id = "participant-slot")
public class ParticipantSlotEntity
                extends EventSourcedEntity<ParticipantSlotEntity.State, ParticipantSlotEntity.Event> {

        public Effect<Done> unmarkAvailable(ParticipantSlotEntity.Commands.UnmarkAvailable unmark) {

                if (currentState() == null || !currentState().status().equals("marked-available")) {
                    return effects().error("ParticipantSlot cannot be unmarked available.");
                }

                String slotId = unmark.slotId;
                String participantId = unmark.participantId;
                ParticipantType participantType = unmark.participantType;

                var event = new ParticipantSlotEntity.Event.UnmarkedAvailable(slotId, participantId, participantType);

                return effects()
                        .persist(event)
                        .thenReply(newState -> Done.getInstance());
        }

        public Effect<Done> markAvailable(ParticipantSlotEntity.Commands.MarkAvailable mark) {

                if (currentState() == null || currentState().status().equals("marked-available")) {
                    return effects().error("ParticipantSlot cannot be marked available.");
                }

                String slotId = mark.slotId;
                String participantId = mark.participantId;
                ParticipantType participantType = mark.participantType;

                var event = new ParticipantSlotEntity.Event.MarkedAvailable(slotId, participantId, participantType);

                return effects()
                        .persist(event)
                        .thenReply(newState -> Done.getInstance());
        }

        public Effect<Done> book(ParticipantSlotEntity.Commands.Book book) {

                if (currentState() == null || !currentState().status().equals("marked-available")) {
                    return effects().error("ParticipantSlot cannot be booked.");
                }

                String slotId = book.slotId;
                String participantId = book.participantId;
                ParticipantType participantType = book.participantType;
                String bookingId = book.bookingId;

                var event = new ParticipantSlotEntity.Event.Booked(slotId, participantId, participantType, bookingId);

                return effects()
                        .persist(event)
                        .thenReply(newState -> Done.getInstance());
        }

        public Effect<Done> cancel(ParticipantSlotEntity.Commands.Cancel cancel) {

                if (currentState() == null || !currentState().status().equals("booked")) {
                    return effects().error("ParticipantSlot cannot be cancelled.");
                }

                var slotId = cancel.slotId;
                String participantId = cancel.participantId;
                ParticipantType participantType = cancel.participantType;
                String bookingId = cancel.bookingId;

                var event = new ParticipantSlotEntity.Event.Canceled(slotId, participantId, participantType, bookingId);

                return effects()
                        .persist(event)
                        .thenReply(newState -> Done.getInstance());
        }

        record State(
                        String slotId, String participantId, ParticipantType participantType, String status) {
        }

        public sealed interface Commands {
                record MarkAvailable(String slotId, String participantId, ParticipantType participantType)
                                implements Commands {
                }

                record UnmarkAvailable(String slotId, String participantId, ParticipantType participantType)
                                implements Commands {
                }

                record Book(
                                String slotId, String participantId, ParticipantType participantType, String bookingId)
                                implements Commands {
                }

                record Cancel(
                                String slotId, String participantId, ParticipantType participantType, String bookingId)
                                implements Commands {
                }
        }

        public sealed interface Event {
                @TypeName("marked-available")
                record MarkedAvailable(String slotId, String participantId, ParticipantType participantType)
                                implements Event {
                }

                @TypeName("unmarked-available")
                record UnmarkedAvailable(String slotId, String participantId, ParticipantType participantType)
                                implements Event {
                }

                @TypeName("participant-booked")
                record Booked(
                                String slotId, String participantId, ParticipantType participantType, String bookingId)
                                implements Event {
                }

                @TypeName("participant-canceled")
                record Canceled(
                                String slotId, String participantId, ParticipantType participantType, String bookingId)
                                implements Event {
                }
        }

        @Override
        public ParticipantSlotEntity.State applyEvent(ParticipantSlotEntity.Event event) {
                switch (event) {
                    case ParticipantSlotEntity.Event.MarkedAvailable evt ->
                        new State(evt.slotId, evt.participantId, evt.participantType, "marked-available");
                    case ParticipantSlotEntity.Event.UnmarkedAvailable evt ->
                        new State(evt.slotId, evt.participantId, evt.participantType, "marked-unavailable");
                    case ParticipantSlotEntity.Event.Booked evt ->
                            new State(evt.slotId, evt.participantId, evt.participantType, "booked");
                    case ParticipantSlotEntity.Event.Canceled evt ->
                            new State(evt.slotId, evt.participantId, evt.participantType, "canceled");
                }
                return null;
        }
}
