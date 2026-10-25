#version 330
#extension GL_ARB_separate_shader_objects : require

// Glade motion blur: this frame mixed with the blended frames before it.
uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

layout(std140) uniform MotionConfig {
    float Strength;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 now = texture(InSampler, texCoord);
    vec4 before = texture(PrevSampler, texCoord);
    fragColor = vec4(mix(now.rgb, before.rgb, Strength), 1.0);
}
