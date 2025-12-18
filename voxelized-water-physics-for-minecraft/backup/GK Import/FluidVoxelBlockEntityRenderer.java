// Full impl ~200 lines, port draw() logic
// Use VertexConsumer for translucent cube quads, apply interp translate, scale, offset, wave
// Color lerp based on type/temp/pressure/vel/foam
// Time for wave: (float) (Util.getMillis() * 0.001 * waveSpeed)
// See example in response for structure, expand as needed.