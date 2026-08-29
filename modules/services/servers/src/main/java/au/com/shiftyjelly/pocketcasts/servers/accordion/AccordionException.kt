package au.com.shiftyjelly.pocketcasts.servers.accordion

/**
 * Raised when the Accordion API could not be queried or answered with something unusable.
 *
 * Carries a message describing the actual cause (bad key, auth redirect, missing route) so the
 * failure is identifiable in the debug log rather than appearing as a generic parse error.
 */
class AccordionException(message: String) : Exception(message)
