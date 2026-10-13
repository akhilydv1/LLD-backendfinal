package com.designloop.service;

import com.designloop.domain.ProblemDocument;
import com.designloop.repository.ProblemRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProblemSeeder implements ApplicationRunner {
    private final ProblemRepository repository;

    public ProblemSeeder(ProblemRepository repository) { this.repository = repository; }

    @Override
    public void run(ApplicationArguments args) {
        seedCanonical(() -> problem("parking-lot", "Parking Lot with Safe Checkout", "MEDIUM", 45,
                "Design the object model for one office parking facility with multiple floors. Focus on spot ownership, ticket lifecycle, and safe checkout when payment fails.",
                "State and ownership", List.of("Class design", "State management", "Failure handling", "Testability"),
                req("R1", "Support BIKE, CAR and TRUCK vehicles and spot types. A BIKE fits any type; a CAR fits CAR or TRUCK; a TRUCK fits only TRUCK. Prefer the smallest compatible free spot; document a deterministic tie-break.") ,
                req("R2", "Entry issues one active ticket per vehicle and reserves exactly one spot. Reject duplicate active entry or a full facility without changing occupancy. Explain how concurrent entrants cannot reserve the same spot."),
                req("R3", "A ticket retains vehicle identity, reserved spot and entry time. Exit fees round elapsed time up to whole hours with a minimum of one hour: BIKE 20, CAR 40, TRUCK 80 currency units per hour. Reject exit times before entry."),
                req("R4", "Checkout uses an external payment boundary. Failed payment leaves the ticket active and spot occupied. Successful checkout closes the ticket and frees the spot. Repeated checkout must not charge twice."),
                req("R5", "Keep business invariants with their owners. Provide testable time, tariff and payment dependencies. Explain entry and failed-then-successful checkout interactions."),
                List.of("Vehicle registration is normalized at the request boundary.", "Money must not use floating-point arithmetic.", "One in-process facility; local synchronization is enough for this exercise."),
                List.of("Reservations, staff screens, login, distributed locks, multi-region deployment and a real payment-provider integration."),
                "The office introduces a weekend flat-rate tariff. Explain which classes change, which remain untouched, and how existing ticket behaviour is tested."));

        seedCanonical(() -> problem("elevator-system", "Elevator Control System", "MEDIUM", 40,
                "Design a controller for several elevators in one building, including requests, movement state, dispatching and safety.",
                "State and coordination", List.of("State machines", "Scheduling", "Interfaces", "Edge cases"),
                req("R1", "Support multiple elevators, internal destination requests and external up/down hall calls. Validate floor numbers and capacity."),
                req("R2", "Track each elevator's current floor, direction, door state, load and operational state. Disallow impossible transitions such as moving with doors open."),
                req("R3", "Dispatch requests using a documented policy and explain how pending requests are assigned, merged or re-assigned."),
                req("R4", "Handle overload, maintenance mode, stuck elevator or emergency stop without continuing normal dispatch for an unavailable elevator."),
                req("R5", "Keep clock/scheduling policy testable and explain how priority or service elevators could be added."),
                List.of("One building with a finite set of floors and elevators.", "The exercise models decisions, not motor-control hardware."),
                List.of("Physical controls, real-time safety certification, multiple buildings and telemetry infrastructure."),
                "Introduce priority service for emergency personnel. Explain which policy or class changes and how normal dispatch is regression-tested."));

        seedCanonical(() -> problem("vending-machine", "Vending Machine", "EASY", 30,
                "Design a vending machine that manages product inventory and a purchase lifecycle while handling payment and dispense failures.",
                "State and transactions", List.of("Encapsulation", "Transactions", "State transitions", "Testing"),
                req("R1", "Represent product identity, price and stock quantity. Prevent sales of unknown or unavailable products."),
                req("R2", "Validate selection and payment before dispensing. Insufficient payment must not reduce stock."),
                req("R3", "Model purchase states so a retry cannot dispense twice or charge twice."),
                req("R4", "Handle dispense failure, cancelled purchase, change/refund policy and a payment-provider timeout."),
                req("R5", "Separate inventory, payment and dispensing responsibilities and explain how time and external dependencies can be faked in tests."),
                List.of("One machine and one purchase session are in scope.", "Use integer minor units or a decimal money type."),
                List.of("Network of machines, inventory replenishment UI and actual payment terminals."),
                "Add cashless loyalty discounts. Explain where the discount policy belongs and how stock/payment behaviour remains unchanged."));

        seedCanonical(() -> problem("splitwise", "Expense Sharing", "MEDIUM", 40,
                "Design a small group expense tracker with expenses, participants, split rules, balances and settlement suggestions.",
                "Domain modelling", List.of("Domain entities", "Money", "Validation", "Extensibility"),
                req("R1", "Create groups and members and record an expense paid by one member and shared by selected participants."),
                req("R2", "Support equal and exact-amount splits. Reject split totals that do not equal the expense amount."),
                req("R3", "Calculate each member's net balance accurately without floating-point money errors."),
                req("R4", "Handle removing members, duplicate expense submission, invalid participants and settlement recording."),
                req("R5", "Keep split calculation testable and explain how a new percentage-based split type would be added."),
                List.of("A group has a single currency.", "Amounts are stored in minor units or a suitable decimal representation."),
                List.of("Bank integration, recurring bills, foreign-exchange conversion and public social features."),
                "Add percentage-based splits. Explain how a SplitStrategy abstraction (or a simpler alternative) would be tested without breaking existing split types."));

        seedCanonical(() -> problem("library-management", "Library Management", "EASY", 30,
                "Model book titles and physical copies, members, loans, returns, due dates and renewals.",
                "Entities and policies", List.of("Composition", "Lifecycle", "Business rules", "Date testing"),
                req("R1", "Separate a book title from individual physical copies that can independently be available or loaned."),
                req("R2", "Allow eligible members to borrow an available copy and create a loan with issue and due dates."),
                req("R3", "Reject checkout when no copy is available, the member is ineligible or the loan limit is reached."),
                req("R4", "Handle returns, overdue status, renewal limits and invalid repeated transitions."),
                req("R5", "Use a testable clock and explain how holds or reservations would affect availability."),
                List.of("One library branch and one timezone are in scope.", "A copy can have at most one active loan."),
                List.of("Supplier purchasing, multi-branch transfers, online fines payment and authentication."),
                "Add reservations/holds. Explain how the queue interacts with returns and how date-sensitive rules can be regression-tested."));

        seedCanonical(() -> problem("food-delivery", "Food Delivery Order Lifecycle", "HARD", 55,
                "Design the core object model and workflow for food orders, restaurant acceptance, courier assignment and delivery updates.",
                "Workflow design", List.of("State transitions", "Strategy", "Idempotency", "Events"),
                req("R1", "Represent customers, restaurants, menu items, orders, order lines and delivery partners with clear ownership."),
                req("R2", "Define valid order state transitions from creation through restaurant acceptance, preparation, pickup and delivery."),
                req("R3", "Handle restaurant rejection, item unavailability and delivery assignment failure with a documented customer outcome."),
                req("R4", "Avoid duplicate payment, notification or assignment effects when commands/events are retried."),
                req("R5", "Keep delivery assignment and pricing policies replaceable only where useful; describe how event handling is tested."),
                List.of("One order uses a single restaurant and one delivery partner at a time.", "External payment and notification providers are interfaces/fakes."),
                List.of("Multi-region dispatch, route optimization engine and production payment integration."),
                "Introduce surge delivery pricing. Explain which pricing component changes and why order lifecycle rules remain stable."));
    }

    private void seedCanonical(java.util.function.Supplier<ProblemDocument> supplier) {
        // Keep the bundled catalogue canonical across backend upgrades. Existing learner attempts
        // remain untouched; only the six known problem records are refreshed from source data.
        repository.save(supplier.get());
    }

    private ProblemDocument problem(String id, String title, String difficulty, int minutes, String description,
                                    String category, List<String> skills, ProblemDocument.Requirement r1,
                                    ProblemDocument.Requirement r2, ProblemDocument.Requirement r3,
                                    ProblemDocument.Requirement r4, ProblemDocument.Requirement r5,
                                    List<String> assumptions, List<String> outOfScope, String changeScenario) {
        ProblemDocument p = new ProblemDocument();
        p.setId(id); p.setTitle(title); p.setDifficulty(difficulty); p.setSuggestedMinutes(minutes);
        p.setDescription(description); p.setCategory(category); p.setSkills(skills);
        p.setRequirements(List.of(r1, r2, r3, r4, r5)); p.setAssumptions(assumptions);
        p.setOutOfScope(outOfScope); p.setChangeScenario(changeScenario); p.setRubricVersion("lld-v1");
        p.setCriteria(List.of(
                new ProblemDocument.RubricCriterion("REQUIREMENTS", "Requirement understanding", 15, "Explain the stated rules, constraints and assumptions.", "requirements"),
                new ProblemDocument.RubricCriterion("RESPONSIBILITIES", "Class responsibilities", 20, "Look for cohesive responsibilities and clear owners of invariants.", "responsibilities"),
                new ProblemDocument.RubricCriterion("COUPLING_COHESION", "Coupling and cohesion", 15, "Assess relationships, dependency direction and unnecessary cross-object knowledge.", "relationships"),
                new ProblemDocument.RubricCriterion("ENCAPSULATION", "Encapsulation and interfaces", 10, "State changes should preserve invariants through clear behaviour.", "behavior"),
                new ProblemDocument.RubricCriterion("ABSTRACTION", "Appropriate abstraction", 10, "Reward justified simplicity and abstractions, not pattern names alone.", "tradeoffs"),
                new ProblemDocument.RubricCriterion("EXTENSIBILITY", "Extensibility", 10, "Evaluate the provided change scenario and expected change cost.", "tradeoffs"),
                new ProblemDocument.RubricCriterion("EDGE_CASES_TESTABILITY", "Edge cases and testability", 15, "Assess failure paths, tests and injectable external dependencies.", "tests"),
                new ProblemDocument.RubricCriterion("EXPLANATION", "Quality of explanation", 5, "Check whether a main interaction and trade-offs can be traced.", "behavior")
        ));
        return p;
    }

    private ProblemDocument.Requirement req(String id, String description) {
        return new ProblemDocument.Requirement(id, description);
    }
}
