package au.com.shiftyjelly.pocketcasts.servers.accordion

import javax.inject.Qualifier

/**
 * The Accordion developer API key (see [AccordionConfig.API_KEY]).
 *
 * Injected rather than read from [AccordionConfig] directly so the key is a normal dependency:
 * [AccordionManager]'s handling of a missing or rejected key is then testable without a build that
 * carries a real one.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AccordionApiKey
