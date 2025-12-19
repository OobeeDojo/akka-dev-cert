package io.example.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import io.example.domain.Participant.ParticipantType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "participant-slot")
public class ParticipantSlotEntity
                extends EventSourcedEntity<ParticipantSlotEntity.State, ParticipantSlotEntity.Event> {

        private static Logger logger = LoggerFactory.getLogger(ParticipantSlotEntity.class);

        public Effect<Done> unmarkAvailable(ParticipantSlotEntity.Commands.UnmarkAvailable unmark) {
                logger.info("Unmarking participant {} available for slot {}...", unmark.participantId, unmark.slotId);

                if (currentState() == null) {
                    logger.error("Slot {} doesn't exist.", unmark.slotId);
                    return effects().reply(Done.getInstance());
                } else {
                    if (currentState().status().equals("unavailable")) {
                        logger.warn("Participant {} already unmarked available for slot {}.", unmark.participantId, unmark.slotId);
                        return effects().reply(Done.getInstance());
                    }
                    if (currentState().status().equals("booked")) {
                        logger.warn("Participant {} is booked for slot {}. You must cancel reservation before unmarking availability.", unmark.participantId, unmark.slotId);
                        return effects().reply(Done.getInstance());
                    }
                }

                return effects()
                        .persist(new ParticipantSlotEntity.Event.UnmarkedAvailable(
                                unmark.slotId,
                                unmark.participantId,
                                unmark.participantType))
                        .thenReply(__ -> Done.getInstance());
        }

        public Effect<Done> markAvailable(ParticipantSlotEntity.Commands.MarkAvailable mark) {
                logger.info("Marking participant {} available for slot {}...", mark.participantId, mark.slotId);

                if (currentState() != null) {
                    if (currentState().status().equals("available")) {
                        logger.warn("Participant {} already marked available for slot {}.", mark.participantId, mark.slotId);
                        return effects().reply(Done.getInstance());
                    }
                    if (currentState().status().equals("booked")) {
                        logger.warn("Participant {} has already been booked for slot {}.", mark.participantId, mark.slotId);
                        return effects().reply(Done.getInstance());
                    }
                }

                return effects()
                        .persist(new ParticipantSlotEntity.Event.MarkedAvailable(
                                mark.slotId,
                                mark.participantId,
                                mark.participantType))
                        .thenReply(__ -> Done.getInstance());
        }

        public Effect<Done> book(ParticipantSlotEntity.Commands.Book book) {
                logger.info("Booking slot {} for participant {}", book.slotId, book.participantId);

                if (currentState() == null) {
                    logger.error("Slot {} doesn't exist.", book.slotId);
                    return effects().reply(Done.getInstance());
                } else {
                    if (currentState().status().equals("unavailable")) {
                        logger.warn("Participant {} unavailable for slot {}.", book.participantId, book.slotId);
                        return effects().reply(Done.getInstance());
                    }
                    if (currentState().status().equals("booked")) {
                        logger.warn("Participant {} has already been booked for slot {}. Booking ref is {}.", book.participantId, book.slotId, book.bookingId);
                        return effects().reply(Done.getInstance());
                    }
                }

                return effects()
                        .persist(new ParticipantSlotEntity.Event.Booked(
                                book.slotId,
                                book.participantId,
                                book.participantType,
                                book.bookingId))
                        .thenReply(__ -> Done.getInstance());
        }

        public Effect<Done> cancel(ParticipantSlotEntity.Commands.Cancel cancel) {
            logger.info("Canceling slot {} for participant {}", cancel.slotId, cancel.participantId);

                if (currentState() == null) {
                    logger.error("Slot doesn't exist.");
                    return effects().reply(Done.getInstance());
                }

                if (!currentState().status().equals("booked")) {
                    logger.warn("Slot hasn't been booked yet.");
                    return effects().reply(Done.getInstance());
                }

                return effects()
                        .persist(new ParticipantSlotEntity.Event.Canceled(
                                cancel.slotId,
                                cancel.participantId,
                                cancel.participantType,
                                cancel.bookingId))
                        .thenReply(__ -> Done.getInstance());
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
                return switch (event) {
                    case ParticipantSlotEntity.Event.MarkedAvailable evt ->
                        new State(evt.slotId, evt.participantId, evt.participantType, "available");
                    case ParticipantSlotEntity.Event.UnmarkedAvailable evt ->
                        new State(evt.slotId, evt.participantId, evt.participantType, "unavailable");
                    case ParticipantSlotEntity.Event.Booked evt ->
                            new State(evt.slotId, evt.participantId, evt.participantType, "booked");
                    case ParticipantSlotEntity.Event.Canceled evt ->
                            new State(evt.slotId, evt.participantId, evt.participantType, "available");
                };
//                return null;
        }
}
