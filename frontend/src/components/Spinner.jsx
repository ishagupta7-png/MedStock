/**
 * Shared busy indicators. Three shapes, because the situations differ:
 * `Spinner` is the bare ring, `LoadingState` replaces a whole panel that has nothing to show
 * yet, and `ButtonBusy` sits inside a button whose request is in flight.
 */
export default function Spinner({ size = "md", tone = "brand" }) {
  const classes = ["spinner"];
  if (size === "sm") classes.push("spinner-sm");
  if (tone === "on-dark") classes.push("spinner-on-dark");
  if (tone === "inherit") classes.push("spinner-inherit");
  return <span className={classes.join(" ")} aria-hidden="true" />;
}

export function LoadingState({ label = "Loading..." }) {
  return (
    <div className="loading-state" role="status" aria-live="polite">
      <Spinner />
      <span>{label}</span>
    </div>
  );
}

/**
 * Button label while its request is in flight. Keeps the ring and text on one line so the button
 * does not change height and shift the row around it.
 *
 * `tone` has to match the button: btn-primary is filled, so the ring is white; btn-secondary and
 * btn-danger are white-backgrounded, where a white ring would be invisible - those inherit the
 * button's own text colour instead.
 */
export function ButtonBusy({ label, tone = "on-dark" }) {
  return (
    <span className="btn-spinner-row">
      <Spinner size="sm" tone={tone} /> {label}
    </span>
  );
}