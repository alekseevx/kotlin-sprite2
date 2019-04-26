import sdl2.*
import sdl2_test.*
import kotlinx.cinterop.*
import kotlin.random.*

data class Point(var x: Int = 0, var y: Int = 0)
data class Size(var w: Int = 0, var h: Int = 0)

class Sprite {
    val pos: Point
    val size: Size
    val velocities: Point
    val texture: CPointer<SDL_Texture>

    constructor(state: CPointer<SDLTest_CommonState>, image: CPointer<SDL_Surface>) {
        this.pos = Point(
            Random.nextInt(0, state[0].window_w - image[0].w),
            Random.nextInt(0, state[0].window_h - image[0].h)
        )
        this.size = Size(image[0].w, image[0].h)

        this.velocities = Point()
        while (this.velocities.x == 0 && this.velocities.y == 0) {
            this.velocities.x = Random.nextInt(-2, 2)
            this.velocities.y = Random.nextInt(-2, 2)
        }

        val texture = SDL_CreateTextureFromSurface(state[0].renderers!![0], image)
        if (texture == null) {
            val error = SDL_GetError()?.toKString()
            throw RuntimeException("Could not create image: ${error}")
        }
        SDL_SetTextureBlendMode(texture, SDL_BLENDMODE_BLEND)
        this.texture = texture
    }
}

fun loadImage(fileName: String): CPointer<SDL_Surface> {
    val file = SDL_RWFromFile(fileName, "rb")
    if (file == null) {
        val error = SDL_GetError()?.toKString()
        throw RuntimeException("Could not load image: ${error}")
    }
    val image = SDL_LoadBMP_RW(file, 1)
    if (image == null) {
        val error = SDL_GetError()?.toKString()
        throw RuntimeException("Could not load image ${error}")
    }

    return image
}

fun makeSprites(state: CPointer<SDLTest_CommonState>, fileName: String, count: Int): List<Sprite> {
    val image = loadImage(fileName)

    val sprites = ArrayList<Sprite>()
    try {
        for (i in 0 until count) {
            sprites.add(Sprite(state, image))
        }
    }
    finally {
        SDL_FreeSurface(image)
    }

    return sprites
}

fun Array<String>.toArgv(autofreeScope: AutofreeScope): CPointer<CPointerVar<ByteVar>> {
    val argv = autofreeScope.allocArray<CPointerVar<ByteVar>>(this.size + 2)
    argv[0] = "sprite2".cstr.getPointer(autofreeScope)
    for (i in 0 until this.size) {
        argv[i + 1] = this[i].cstr.getPointer(autofreeScope)
    }
    return argv
}


fun makeSDLTestState(args: Array<String>): CPointer<SDLTest_CommonState> {
    memScoped {
        var argv  = args.toArgv(this)
        val state = SDLTest_CommonCreateState(argv, SDL_INIT_VIDEO)
        if (state == null) {
            val error = SDL_GetError()?.toKString()
            throw RuntimeException("Could not create SDLTest_SDLTest_CommonState: ${error}")
        }

        for (i in 1 until args.size + 1) {
            SDLTest_CommonArg(state, i);
        }

        if (SDLTest_CommonInit(state) == SDL_FALSE) {
            val error = SDL_GetError()?.toKString()
            throw RuntimeException("SDLTest_CommonInit failed: ${error}")
        }

        return state
    }
}

fun moveSprites(state: CPointer<SDLTest_CommonState>, sprites: List<Sprite>) {

    memScoped {
        val renderer = state[0].renderers!!.get(0)

        SDL_SetRenderDrawColor(renderer, 0xA0, 0xA0, 0xA0, 0xFF)
        SDL_RenderClear(renderer)

        val spriteRect = alloc<SDL_Rect>()
        val viewport = alloc<SDL_Rect>()
        SDL_RenderGetViewport(renderer, viewport.ptr);
        for (sprite in sprites) {
            sprite.pos.x += sprite.velocities.x
            if (sprite.pos.x < 0 || sprite.pos.x >= (viewport.w - sprite.size.w)) {
                sprite.velocities.x *= -1
                sprite.pos.x += sprite.velocities.x
            }
            sprite.pos.y += sprite.velocities.y
            if (sprite.pos.y < 0 || sprite.pos.y >= (viewport.h - sprite.size.h)) {
                sprite.velocities.y *= -1
                sprite.pos.y += sprite.velocities.y
            }

            spriteRect.x = sprite.pos.x
            spriteRect.y = sprite.pos.y
            spriteRect.w = sprite.size.w
            spriteRect.h = sprite.size.h

            SDL_RenderCopy(renderer, sprite.texture, null, spriteRect.ptr)
        }

        SDL_RenderPresent(renderer)
    }
}

fun processEvents(state: CPointer<SDLTest_CommonState>): Boolean {
    memScoped {
        val done =  alloc<IntVar>()
        val event = alloc<SDL_Event>()

        while (SDL_PollEvent(event.ptr) != 0) {
            SDLTest_CommonEvent(state, event.ptr, done.ptr)
        }

        return done.value != 0 
    }
}

fun main(args: Array<String>) {
    val state = makeSDLTestState(args)

    try {
        val fpsCheckDelay: UInt = 5000u
        var nextFpsCheck = SDL_GetTicks() + fpsCheckDelay
        var frames = 0u;
        val sprites = makeSprites(state, "icon.bmp", 100)
        
        while (!processEvents(state)) {
            moveSprites(state, sprites);
            
            ++frames;
            val now = SDL_GetTicks()
            if ((nextFpsCheck - now).toInt() <= 0) {
                val then = nextFpsCheck - fpsCheckDelay
                val fps = 1000*frames.toDouble() / (now - then).toDouble()
                
                SDL_Log("%2.2f frames per second\n", fps);
                
                nextFpsCheck = now + fpsCheckDelay
                frames = 0u;
            }
        }
    }
    finally {
        SDLTest_CommonQuit(state)
    }
}
