package com.umd.stobooking.service;

import com.umd.stobooking.exception.CancellationTooLateException;
import com.umd.stobooking.exception.SlotUnavailableException;
import com.umd.stobooking.model.*;
import com.umd.stobooking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private BookingService bookingService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private Client client;
    private Car car;
    private ServiceItem service;

    @BeforeEach
    void setUp() {
        client = new Client();
        client.setId(1L);
        client.setTelegramUserId(111L);
        client.setFirstName("Ivan");

        CarBrand brand = new CarBrand();
        brand.setId(1L);
        brand.setName("Toyota");

        CarModel model = new CarModel();
        model.setId(1L);
        model.setName("Camry");

        car = new Car();
        car.setId(1L);
        car.setClient(client);
        car.setBrand(brand);
        car.setModel(model);

        service = new ServiceItem();
        service.setId(1L);
        service.setName("Замена масла");
        service.setDurationMinutes(45);
    }

    // ── createBooking ─────────────────────────────────────────────────────────

    @Test
    void createBooking_happyPath_savesPendingBookingWithCorrectEndAt() {
        LocalDateTime scheduledAt = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);

        when(bookingRepository.findOverlapping(any(), any(), any())).thenReturn(List.of());
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.createBooking(client, car, service, scheduledAt);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(result.getScheduledAt()).isEqualTo(scheduledAt);
        assertThat(result.getEndAt()).isEqualTo(scheduledAt.plusMinutes(45));
        assertThat(result.getClient()).isSameAs(client);
        assertThat(result.getCar()).isSameAs(car);
        assertThat(result.getService()).isSameAs(service);

        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void createBooking_throwsWhenSlotOverlaps() {
        // findOverlapping returns an existing booking — slot is taken
        Booking conflict = new Booking();
        conflict.setId(99L);
        when(bookingRepository.findOverlapping(any(), any(), any())).thenReturn(List.of(conflict));

        LocalDateTime scheduledAt = LocalDateTime.now().plusDays(1);

        assertThatThrownBy(() -> bookingService.createBooking(client, car, service, scheduledAt))
                .isInstanceOf(SlotUnavailableException.class);

        verify(bookingRepository, never()).save(any());
    }

    // ── createBookingWithProblem ───────────────────────────────────────────────

    @Test
    void createBookingWithProblem_happyPath_savesBookingWithNullServiceAndDefaultDuration() {
        // Problem-path booking: no service selected, 60-min default duration
        LocalDateTime scheduledAt = LocalDateTime.now().plusDays(1).withHour(14).withMinute(0).withSecond(0).withNano(0);
        String problem = "Странный стук в двигателе";

        when(bookingRepository.findOverlapping(any(), any(), any())).thenReturn(List.of());
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Booking result = bookingService.createBookingWithProblem(client, car, problem, scheduledAt);

        assertThat(result.getService()).isNull();
        assertThat(result.getProblemDescription()).isEqualTo(problem);
        assertThat(result.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(result.getEndAt()).isEqualTo(scheduledAt.plusMinutes(60));

        verify(bookingRepository).save(any(Booking.class));
    }

    // ── cancelBooking ─────────────────────────────────────────────────────────

    @Test
    void cancelBooking_happyPath_setsStatusCancelled() {
        // Booking is PENDING, more than 2 hours away, belongs to the client
        LocalDateTime farFuture = LocalDateTime.now().plusHours(3);

        Booking booking = pendingBooking(farFuture, client);

        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        bookingService.cancelBooking(10L, client.getTelegramUserId());

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancelBooking_throwsCancellationTooLateException_whenUnder2Hours() {
        // Appointment is only 1 hour away — too late to cancel
        LocalDateTime tooSoon = LocalDateTime.now().plusMinutes(59);

        Booking booking = pendingBooking(tooSoon, client);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(10L, client.getTelegramUserId()))
                .isInstanceOf(CancellationTooLateException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelBooking_throwsIllegalArgument_whenBookingBelongsToDifferentClient() {
        long otherTelegramId = 999L;

        Client otherClient = new Client();
        otherClient.setId(2L);
        otherClient.setTelegramUserId(otherTelegramId);

        Booking booking = pendingBooking(LocalDateTime.now().plusDays(1), otherClient);
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(10L, client.getTelegramUserId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не ваша");

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelBooking_throwsIllegalState_whenAlreadyCancelled() {
        Booking booking = pendingBooking(LocalDateTime.now().plusDays(1), client);
        booking.setStatus(BookingStatus.CANCELLED);

        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(10L, client.getTelegramUserId()))
                .isInstanceOf(IllegalStateException.class);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelBooking_throwsIllegalState_whenAlreadyCompleted() {
        Booking booking = pendingBooking(LocalDateTime.now().plusDays(1), client);
        booking.setStatus(BookingStatus.COMPLETED);

        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(10L, client.getTelegramUserId()))
                .isInstanceOf(IllegalStateException.class);

        verify(bookingRepository, never()).save(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Booking pendingBooking(LocalDateTime scheduledAt, Client owner) {
        Booking b = new Booking();
        b.setId(10L);
        b.setClient(owner);
        b.setCar(car);
        b.setService(service);
        b.setScheduledAt(scheduledAt);
        b.setEndAt(scheduledAt.plusMinutes(45));
        b.setStatus(BookingStatus.PENDING);
        b.setCreatedAt(LocalDateTime.now().minusHours(1));
        return b;
    }
}
