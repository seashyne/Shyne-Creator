import { useEffect, useRef, useState } from 'react'
import { Pause, Play, RotateCcw } from 'lucide-react'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'

type Vec3 = [number, number, number]

type BlockbenchFace = {
  uv?: [number, number, number, number]
  texture?: number | string
}

type BlockbenchCube = {
  name?: string
  type: 'cube'
  from: Vec3
  to: Vec3
  origin?: Vec3
  rotation?: Vec3
  inflate?: number
  export?: boolean
  faces?: Record<string, BlockbenchFace>
}

type BlockbenchTexture = {
  source?: string
  width?: number
  height?: number
  uv_width?: number
  uv_height?: number
}

type BlockbenchModel = {
  elements?: BlockbenchCube[]
  textures?: BlockbenchTexture[]
  resolution?: { width?: number; height?: number }
}

type ViewerController = {
  controls: OrbitControls
}

const MODEL_URL = 'deep-shynecore-model.bbmodel'

const faceDefinitions = [
  { name: 'east', vertices: (x0: number, x1: number, y0: number, y1: number, z0: number, z1: number) => [[x1, y0, z1], [x1, y0, z0], [x1, y1, z0], [x1, y1, z1]] },
  { name: 'west', vertices: (x0: number, x1: number, y0: number, y1: number, z0: number, z1: number) => [[x0, y0, z0], [x0, y0, z1], [x0, y1, z1], [x0, y1, z0]] },
  { name: 'up', vertices: (x0: number, x1: number, y0: number, y1: number, z0: number, z1: number) => [[x0, y1, z1], [x1, y1, z1], [x1, y1, z0], [x0, y1, z0]] },
  { name: 'down', vertices: (x0: number, x1: number, y0: number, y1: number, z0: number, z1: number) => [[x0, y0, z0], [x1, y0, z0], [x1, y0, z1], [x0, y0, z1]] },
  { name: 'south', vertices: (x0: number, x1: number, y0: number, y1: number, z0: number, z1: number) => [[x0, y0, z1], [x1, y0, z1], [x1, y1, z1], [x0, y1, z1]] },
  { name: 'north', vertices: (x0: number, x1: number, y0: number, y1: number, z0: number, z1: number) => [[x1, y0, z0], [x0, y0, z0], [x0, y1, z0], [x1, y1, z0]] },
] as const

function textureIndex(value: BlockbenchFace['texture']) {
  if (typeof value === 'number') return value
  if (typeof value === 'string') {
    const parsed = Number.parseInt(value.replace('#', ''), 10)
    return Number.isFinite(parsed) ? parsed : -1
  }
  return -1
}

function cubeGeometry(cube: BlockbenchCube, model: BlockbenchModel) {
  const origin = cube.origin ?? [0, 0, 0]
  const inflate = cube.inflate ?? 0
  const x0 = cube.from[0] - origin[0] - inflate
  const x1 = cube.to[0] - origin[0] + inflate
  const y0 = cube.from[1] - origin[1] - inflate
  const y1 = cube.to[1] - origin[1] + inflate
  const z0 = cube.from[2] - origin[2] - inflate
  const z1 = cube.to[2] - origin[2] + inflate
  const positions: number[] = []
  const uvs: number[] = []
  const indices: number[] = []
  const geometry = new THREE.BufferGeometry()

  faceDefinitions.forEach((definition, faceIndex) => {
    const face = cube.faces?.[definition.name]
    const texIndex = textureIndex(face?.texture)
    const texture = model.textures?.[texIndex]
    const textureWidth = texture?.uv_width ?? texture?.width ?? model.resolution?.width ?? 16
    const textureHeight = texture?.uv_height ?? texture?.height ?? model.resolution?.height ?? 16
    const [u0, v0, u1, v1] = face?.uv ?? [0, 0, 1, 1]
    const vertices = definition.vertices(x0, x1, y0, y1, z0, z1)
    const base = positions.length / 3

    vertices.forEach((vertex) => positions.push(vertex[0], vertex[1], vertex[2]))
    uvs.push(
      u0 / textureWidth, v1 / textureHeight,
      u1 / textureWidth, v1 / textureHeight,
      u1 / textureWidth, v0 / textureHeight,
      u0 / textureWidth, v0 / textureHeight,
    )
    indices.push(base, base + 1, base + 2, base, base + 2, base + 3)
    geometry.addGroup(faceIndex * 6, 6, faceIndex)
  })

  geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
  geometry.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2))
  geometry.setIndex(indices)
  geometry.computeVertexNormals()
  return geometry
}

export default function ModelViewer() {
  const mountRef = useRef<HTMLDivElement>(null)
  const controllerRef = useRef<ViewerController | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [autoRotate, setAutoRotate] = useState(true)

  useEffect(() => {
    const mount = mountRef.current
    if (!mount) return

    let disposed = false
    const scene = new THREE.Scene()
    const camera = new THREE.PerspectiveCamera(34, 1, 0.1, 300)
    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' })
    renderer.outputColorSpace = THREE.SRGBColorSpace
    renderer.toneMapping = THREE.ACESFilmicToneMapping
    renderer.toneMappingExposure = 1.15
    renderer.shadowMap.enabled = true
    renderer.shadowMap.type = THREE.PCFShadowMap
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    mount.appendChild(renderer.domElement)

    const controls = new OrbitControls(camera, renderer.domElement)
    controls.enableDamping = true
    controls.dampingFactor = 0.065
    controls.autoRotate = true
    controls.autoRotateSpeed = 1.35
    controls.enablePan = false
    controllerRef.current = { controls }

    scene.add(new THREE.HemisphereLight(0xa7efff, 0x10131d, 2.1))
    const keyLight = new THREE.DirectionalLight(0xb9f3ff, 3.2)
    keyLight.position.set(-18, 42, -28)
    keyLight.castShadow = true
    keyLight.shadow.mapSize.set(1024, 1024)
    keyLight.shadow.camera.near = 1
    keyLight.shadow.camera.far = 100
    keyLight.shadow.camera.left = -24
    keyLight.shadow.camera.right = 24
    keyLight.shadow.camera.top = 38
    keyLight.shadow.camera.bottom = -8
    scene.add(keyLight)
    const violetLight = new THREE.PointLight(0x7457ff, 28, 85)
    violetLight.position.set(24, 18, 18)
    scene.add(violetLight)

    const grid = new THREE.GridHelper(58, 12, 0x3cdcf6, 0x26354d)
    const gridMaterial = grid.material as THREE.Material
    gridMaterial.transparent = true
    gridMaterial.opacity = 0.32
    scene.add(grid)

    const shadow = new THREE.Mesh(
      new THREE.PlaneGeometry(38, 38),
      new THREE.ShadowMaterial({ color: 0x02050a, opacity: 0.45 }),
    )
    shadow.rotation.x = -Math.PI / 2
    shadow.position.y = -0.03
    shadow.receiveShadow = true
    scene.add(shadow)

    const resize = () => {
      const width = Math.max(mount.clientWidth, 1)
      const height = Math.max(mount.clientHeight, 1)
      renderer.setSize(width, height, false)
      camera.aspect = width / height
      camera.updateProjectionMatrix()
    }
    const resizeObserver = new ResizeObserver(resize)
    resizeObserver.observe(mount)
    resize()

    const textureLoader = new THREE.TextureLoader()
    const transparentMaterial = new THREE.MeshBasicMaterial({ transparent: true, opacity: 0, depthWrite: false })
    const resources: Array<THREE.BufferGeometry | THREE.Material | THREE.Texture> = [transparentMaterial]

    const loadModel = async () => {
      try {
        const response = await fetch(MODEL_URL)
        if (!response.ok) throw new Error(`Model request failed: ${response.status}`)
        const model = await response.json() as BlockbenchModel
        const textures = await Promise.all((model.textures ?? []).map(async (entry) => {
          if (!entry.source) return null
          const texture = await textureLoader.loadAsync(entry.source)
          texture.colorSpace = THREE.SRGBColorSpace
          texture.magFilter = THREE.NearestFilter
          texture.minFilter = THREE.NearestFilter
          texture.generateMipmaps = false
          texture.flipY = false
          texture.needsUpdate = true
          resources.push(texture)
          return texture
        }))
        const materials = textures.map((texture) => {
          if (!texture) return transparentMaterial
          const material = new THREE.MeshStandardMaterial({
            map: texture,
            transparent: true,
            alphaTest: 0.05,
            roughness: 0.82,
            metalness: 0,
            side: THREE.DoubleSide,
          })
          resources.push(material)
          return material
        })

        const modelGroup = new THREE.Group()
        ;(model.elements ?? []).filter((element) => element.type === 'cube' && element.export !== false).forEach((cube) => {
          const geometry = cubeGeometry(cube, model)
          const faceMaterials = faceDefinitions.map((definition) => {
            const index = textureIndex(cube.faces?.[definition.name]?.texture)
            return materials[index] ?? transparentMaterial
          })
          const mesh = new THREE.Mesh(geometry, faceMaterials)
          const origin = cube.origin ?? [0, 0, 0]
          const rotation = cube.rotation ?? [0, 0, 0]
          mesh.name = cube.name ?? 'Blockbench cube'
          mesh.position.set(origin[0], origin[1], origin[2])
          mesh.rotation.order = 'ZYX'
          mesh.rotation.set(
            THREE.MathUtils.degToRad(rotation[0]),
            THREE.MathUtils.degToRad(rotation[1]),
            THREE.MathUtils.degToRad(rotation[2]),
          )
          mesh.castShadow = true
          mesh.receiveShadow = true
          resources.push(geometry)
          modelGroup.add(mesh)
        })
        scene.add(modelGroup)

        const bounds = new THREE.Box3().setFromObject(modelGroup)
        const center = bounds.getCenter(new THREE.Vector3())
        const size = bounds.getSize(new THREE.Vector3())
        const extent = Math.max(size.x, size.y, size.z)
        controls.target.copy(center)
        camera.position.set(center.x + extent * 0.48, center.y + extent * 0.12, center.z - extent * 1.72)
        camera.near = Math.max(extent / 100, 0.1)
        camera.far = extent * 12
        camera.updateProjectionMatrix()
        controls.minDistance = extent * 0.72
        controls.maxDistance = extent * 3.4
        controls.update()
        controls.saveState()
        if (!disposed) setStatus('ready')
      } catch {
        if (!disposed) setStatus('error')
      }
    }

    void loadModel()
    renderer.setAnimationLoop(() => {
      controls.update()
      renderer.render(scene, camera)
    })

    return () => {
      disposed = true
      renderer.setAnimationLoop(null)
      resizeObserver.disconnect()
      controls.dispose()
      resources.forEach((resource) => resource.dispose())
      renderer.dispose()
      renderer.domElement.remove()
      controllerRef.current = null
    }
  }, [])

  useEffect(() => {
    if (controllerRef.current) controllerRef.current.controls.autoRotate = autoRotate
  }, [autoRotate])

  const resetView = () => {
    controllerRef.current?.controls.reset()
    setAutoRotate(true)
  }

  return <div className="model-viewer">
    <div className="model-canvas" ref={mountRef} role="img" aria-label="โมเดล Deep Shynecore แบบสามมิติที่ลากหมุนและซูมได้" />
    <div className="model-brand"><img src="shyne-icon.png" alt="" /><span><b>DEEP SHYNECORE</b><small>LIVE .BBMODEL</small></span></div>
    <div className={`model-status ${status}`}>
      <i></i>{status === 'loading' ? 'กำลังโหลดโมเดล' : status === 'error' ? 'โหลดโมเดลไม่สำเร็จ' : '3D พร้อมใช้งาน'}
    </div>
    <div className="model-hint">ลากเพื่อหมุน · เลื่อนเพื่อซูม</div>
    <div className="model-controls">
      <button type="button" onClick={() => setAutoRotate((value) => !value)} aria-label={autoRotate ? 'หยุดหมุนอัตโนมัติ' : 'เริ่มหมุนอัตโนมัติ'}>
        {autoRotate ? <Pause size={15}/> : <Play size={15}/>}<span>{autoRotate ? 'หยุดหมุน' : 'หมุนอัตโนมัติ'}</span>
      </button>
      <button type="button" onClick={resetView} aria-label="รีเซ็ตมุมมองโมเดล"><RotateCcw size={15}/><span>รีเซ็ต</span></button>
    </div>
  </div>
}
