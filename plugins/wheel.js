// AirCons live mapping: Wheel.
// Bank the two pods to steer; pitch controls throttle.
function map(input) {
  var bank = input.roll / 28.0;
  var pitch = input.pitch / 24.0;

  function clamp(v) {
    return Math.max(-1, Math.min(1, v));
  }

  return {
    lookX: 0,
    lookY: 0,
    steer: clamp(bank * 1.08),
    throttle: clamp(pitch * 0.85),
    fire: input.trigger
  };
}
