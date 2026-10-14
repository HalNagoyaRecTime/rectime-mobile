import ActivityKit
import Foundation

struct RecreationActivityAttributes: ActivityAttributes {
    struct Round: Codable, Hashable {
        let number: Int
        let start: Date?
        let participating: Bool
    }
    struct Match: Codable, Hashable, Identifiable {
        let id: Int
        let title: String
        let venue: String
        let start: Date
        let end: Date
        let rounds: [Round]
        let roundCount: Int
        let timelineRounds: [Round]?
    }
    struct Gathering: Codable, Hashable {
        let eventId: Int
        let title: String
        let number: Int
        let round: Int
        let time: Date
        let spot: String
        let roundStart: Date?
        let venue: String?
        let eventEnd: Date?
    }
    struct ContentState: Codable, Hashable {
        let accountId: String
        let matches: [Match]
        let matchCount: Int
        let nextGathering: Gathering?
        let nextEvent: Match?
        let validUntil: Date
        let fetchedAt: Date?
        let isCached: Bool
        let notice: String
        let remainingGatheringCount: Int?
        let personalState: String?
        let presentationUntil: Date?
        let displayMode: String?
    }
    let accountId: String
}
