// AirCons live mapping: Headline.
// Return lookX / lookY in -1..1. Fire follows the stem trigger.
function map(input) {
  var yaw = input.yaw / 30.0;
  var pitch = input.pitch / 24.0;

  function shape(v) {
    var magnitude = Math.min(1, Math.abs(v));
    return (v < 0 ? -1 : 1) * Math.pow(magnitude, 1.35);
  }

  return {
    lookX: shape(yaw),
    lookY: shape(pitch),
    steer: 0,
    throttle: 0,
    fire: input.trigger
  };
}
