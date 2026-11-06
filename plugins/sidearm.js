// AirCons live mapping: Sidearm.
// A one-pod pistol: quicker center response, same proportional edges.
function map(input) {
  var yaw = input.yaw / 22.0;
  var pitch = input.pitch / 17.0;

  function snap(v) {
    var magnitude = Math.min(1, Math.abs(v));
    return (v < 0 ? -1 : 1) * (0.35 * magnitude + 0.65 * Math.pow(magnitude, 2.0));
  }

  return {
    lookX: snap(yaw),
    lookY: snap(pitch),
    steer: 0,
    throttle: 0,
    fire: input.trigger
  };
}
