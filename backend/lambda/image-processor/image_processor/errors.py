class RejectedEventError(ValueError):
    """A malformed or irrelevant queue message that must not be retried."""


class RejectedImageError(ValueError):
    """An invalid image that must not be retried."""


class PermanentCallbackError(RuntimeError):
    """A backend callback rejection that repeating the request cannot fix."""
