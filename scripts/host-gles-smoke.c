#define EGL_EGLEXT_PROTOTYPES
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static char *read_file(const char *path) {
    FILE *file = fopen(path, "rb");
    if (!file) {
        perror(path);
        return NULL;
    }
    fseek(file, 0, SEEK_END);
    long length = ftell(file);
    rewind(file);
    char *contents = calloc((size_t)length + 1, 1);
    if (!contents || fread(contents, 1, (size_t)length, file) != (size_t)length) {
        fclose(file);
        free(contents);
        return NULL;
    }
    fclose(file);
    return contents;
}

static GLuint compile_shader(GLenum type, const char *source, const char *name, int *ok) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, NULL);
    glCompileShader(shader);
    GLint status = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &status);
    if (!status) {
        char log[4096];
        GLsizei length = 0;
        glGetShaderInfoLog(shader, sizeof(log), &length, log);
        fprintf(stderr, "  %s COMPILE FAIL: %s\n", name, log);
        *ok = 0;
    }
    return shader;
}

static GLuint build_program(const char *dir, const char *vs_name, const char *fs_name, int *ok) {
    char path[1024];
    snprintf(path, sizeof(path), "%s/%s.glsl", dir, vs_name);
    char *vs_source = read_file(path);
    snprintf(path, sizeof(path), "%s/%s.glsl", dir, fs_name);
    char *fs_source = read_file(path);
    if (!vs_source || !fs_source) {
        free(vs_source);
        free(fs_source);
        *ok = 0;
        return 0;
    }

    int compiled = 1;
    GLuint vs = compile_shader(GL_VERTEX_SHADER, vs_source, vs_name, &compiled);
    GLuint fs = compile_shader(GL_FRAGMENT_SHADER, fs_source, fs_name, &compiled);
    GLuint program = glCreateProgram();
    glAttachShader(program, vs);
    glAttachShader(program, fs);
    glLinkProgram(program);
    GLint linked = 0;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (!linked) {
        char log[4096];
        GLsizei length = 0;
        glGetProgramInfoLog(program, sizeof(log), &length, log);
        fprintf(stderr, "  LINK FAIL: %s\n", log);
    }
    glDeleteShader(vs);
    glDeleteShader(fs);
    free(vs_source);
    free(fs_source);
    *ok = compiled && linked;
    return program;
}

int main(int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "usage: %s SHADER_DIR\n", argv[0]);
        return 2;
    }

    PFNEGLGETPLATFORMDISPLAYEXTPROC get_platform_display =
        (PFNEGLGETPLATFORMDISPLAYEXTPROC)eglGetProcAddress("eglGetPlatformDisplayEXT");
    EGLDisplay display = get_platform_display
        ? get_platform_display(EGL_PLATFORM_SURFACELESS_MESA, EGL_DEFAULT_DISPLAY, NULL)
        : EGL_NO_DISPLAY;
    if (display == EGL_NO_DISPLAY) {
        display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    }

    EGLint major = 0, minor = 0;
    if (display == EGL_NO_DISPLAY || !eglInitialize(display, &major, &minor)) {
        fprintf(stderr, "eglInitialize failed: 0x%x\n", eglGetError());
        return 2;
    }
    eglBindAPI(EGL_OPENGL_ES_API);
    const EGLint config_attrs[] = {
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8,
        EGL_NONE
    };
    EGLConfig config;
    EGLint config_count = 0;
    if (!eglChooseConfig(display, config_attrs, &config, 1, &config_count) || !config_count) {
        fprintf(stderr, "eglChooseConfig for GLES3 pbuffer failed: 0x%x\n", eglGetError());
        return 2;
    }
    const EGLint pbuffer_attrs[] = {EGL_WIDTH, 64, EGL_HEIGHT, 64, EGL_NONE};
    EGLSurface surface = eglCreatePbufferSurface(display, config, pbuffer_attrs);
    const EGLint context_attrs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, context_attrs);
    if (surface == EGL_NO_SURFACE || context == EGL_NO_CONTEXT ||
        !eglMakeCurrent(display, surface, surface, context)) {
        fprintf(stderr, "creating/making current an EGL GLES3 context failed: 0x%x\n", eglGetError());
        return 2;
    }

    printf("EGL %d.%d | GL_VENDOR=%s | GL_RENDERER=%s | GL_VERSION=%s\n",
           major, minor, glGetString(GL_VENDOR), glGetString(GL_RENDERER), glGetString(GL_VERSION));
    struct {
        const char *name;
        const char *vs;
        const char *fs;
    } tests[] = {
        {"mesh", "vertexShader", "fragmentShader"},
        {"fallback", "fallbackVertexShader", "fallbackFragmentShader"},
        {"sky", "skyVertexShader", "skyFragmentShader"},
        {"grid", "gridVertexShader", "gridFragmentShader"},
        {"grid_fallback", "gridVertexShader", "gridFallbackFragmentShader"},
        {"flat", "flatVertexShader", "flatFragmentShader"},
        {"line", "lineVertexShader", "lineFragmentShader"},
        {"tgizmo", "tGizmoVertexShader", "tGizmoFragmentShader"}
    };

    int failures = 0;
    GLuint flat_program = 0;
    for (size_t i = 0; i < sizeof(tests) / sizeof(tests[0]); i++) {
        int ok = 0;
        GLuint program = build_program(argv[1], tests[i].vs, tests[i].fs, &ok);
        printf("%-14s %s\n", tests[i].name, ok ? "COMPILE+LINK OK" : "FAIL");
        if (!ok) {
            failures++;
        }
        if (strcmp(tests[i].name, "flat") == 0) {
            flat_program = program;
        } else if (program) {
            glDeleteProgram(program);
        }
    }

    int pixel_ok = 0;
    if (flat_program) {
        const GLfloat vertices[] = {
            -0.8f, -0.8f, 0.0f,
             0.8f, -0.8f, 0.0f,
             0.0f,  0.8f, 0.0f
        };
        GLuint vao, vbo;
        glGenVertexArrays(1, &vao);
        glBindVertexArray(vao);
        glGenBuffers(1, &vbo);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 3 * sizeof(GLfloat), NULL);
        glViewport(0, 0, 64, 64);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        glUseProgram(flat_program);
        const GLfloat identity[] = {
            1, 0, 0, 0,  0, 1, 0, 0,  0, 0, 1, 0,  0, 0, 0, 1
        };
        glUniformMatrix4fv(glGetUniformLocation(flat_program, "uMVP"), 1, GL_FALSE, identity);
        glUniform4f(glGetUniformLocation(flat_program, "uColor"), 0.25f, 0.5f, 0.75f, 1.0f);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glFinish();

        GLubyte rgba[4] = {0, 0, 0, 0};
        glReadPixels(32, 32, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
        GLenum error = glGetError();
        pixel_ok = error == GL_NO_ERROR &&
            rgba[0] >= 62 && rgba[0] <= 66 &&
            rgba[1] >= 126 && rgba[1] <= 130 &&
            rgba[2] >= 190 && rgba[2] <= 194 &&
            rgba[3] >= 254;
        printf("flat triangle center RGBA=(%u,%u,%u,%u) GL_ERROR=0x%x %s\n",
               rgba[0], rgba[1], rgba[2], rgba[3], error, pixel_ok ? "PIXEL OK" : "PIXEL FAIL");
        if (!pixel_ok) {
            failures++;
        }
        glDeleteBuffers(1, &vbo);
        glDeleteVertexArrays(1, &vao);
        glDeleteProgram(flat_program);
    } else {
        fprintf(stderr, "flat program unavailable; skipping pixel test\n");
        failures++;
    }

    printf("SUMMARY: %d program/pixel failures; flat pixel test %s\n",
           failures, pixel_ok ? "passed" : "failed");
    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(display, context);
    eglDestroySurface(display, surface);
    eglTerminate(display);
    return failures ? 1 : 0;
}
