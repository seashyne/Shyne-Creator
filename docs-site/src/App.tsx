import { lazy, Suspense, useEffect, useMemo, useState, type ReactNode } from 'react'
import { HashRouter, Link, NavLink, Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  Activity, ArrowLeft, ArrowRight, BookOpen, Box, Boxes, Braces, Check, ChevronDown,
  ChevronRight, Cloud, Code2, Copy, Download, ExternalLink, FileCode2, Gamepad2,
  Layers, Menu, MessageCircle, Palette, Play, Search, ShieldCheck, Sparkles, Users, Workflow, Wrench, X, Zap,
  type LucideIcon,
} from 'lucide-react'
import { docs, fileToSlug, minecraftVersion, version, type DocIcon, type DocItem } from './content'

const CURSEFORGE_URL = 'https://www.curseforge.com/minecraft/mc-mods/shyne-creator'
const YOUTUBE_URL = 'https://www.youtube.com/@seashyne'
const CONTACT_URL = 'https://github.com/seashyne/Shyne-Creator/issues'
const DISCORD_URL = 'https://discord.gg/YMFCAXddj3'
const ModelViewer = lazy(() => import('./ModelViewer'))

const siteAsset = (path: string) => `${import.meta.env.BASE_URL}${path}`

type ShowcaseAvatar = {
  id: string
  label: string
  title: string
  description: string
  avatar: string
  model: string
  tags: string[]
}

const showcaseAvatars: ShowcaseAvatar[] = [
  {
    id: 'action-wheel',
    label: 'READY-TO-PLAY AVATAR',
    title: 'Action Wheel Showcase',
    description: 'Avatar ตัวอย่างพร้อมท่าทาง, เสียง, ฟิสิกส์หูและหาง รวมถึง Action Wheel สำหรับทดลองคำสั่งในเกม',
    avatar: 'model-showcase/action-wheel-showcase-avatar.zip',
    model: 'model-showcase/action-wheel-showcase.bbmodel',
    tags: ['Animation', 'Sound', 'Action Wheel'],
  },
  {
    id: 'custom-skill-hud',
    label: 'READY-TO-PLAY AVATAR',
    title: 'Custom Skill & Mana HUD',
    description: 'Avatar ตัวอย่างสำหรับทดลองสกิล, แป้นลัด, แถบ Mana บนหน้าจอ และเอฟเฟกต์โล่เวทมนตร์',
    avatar: 'model-showcase/custom-skill-hud-avatar.zip',
    model: 'model-showcase/custom-skill-hud-avatar.bbmodel',
    tags: ['Custom Skill', 'HUD', 'Keybind'],
  },
  {
    id: 'signature-weapon',
    label: 'READY-TO-PLAY AVATAR',
    title: 'Signature Weapon',
    description: 'Avatar ตัวอย่างอาวุธประจำตัว, ท่าต่อสู้, การบิน และระบบแยกโมดูลพร้อมติดตั้งทดลองได้ทันที',
    avatar: 'model-showcase/signature-weapon-avatar.zip',
    model: 'model-showcase/signature-weapon.bbmodel',
    tags: ['Weapon', 'Flight', 'Animation'],
  },
]

const iconMap: Record<DocIcon, LucideIcon> = {
  book: BookOpen, download: Download, sparkles: Sparkles, box: Box, play: Play,
  layers: Layers, braces: Braces, palette: Palette, gamepad: Gamepad2, cloud: Cloud,
  shield: ShieldCheck, users: Users, wrench: Wrench, workflow: Workflow,
}

const categories = ['เริ่มต้น', 'Blockbench', 'สร้าง Avatar', 'API และระบบ', 'เผยแพร่และพัฒนา'] as const

function Brand() {
  return <Link className="brand" to="/" aria-label="Shyne Creator หน้าหลัก">
    <img src="shyne-icon.png" alt="" />
    <span>SHYNE <b>CREATOR</b></span><em>DOCS</em>
  </Link>
}

function AppShell() {
  const location = useLocation()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const [searchOpen, setSearchOpen] = useState(false)
  const [query, setQuery] = useState('')
  const isHome = location.pathname === '/'

  useEffect(() => {
    setMenuOpen(false)
    window.scrollTo({ top: 0, behavior: 'instant' })
    const activeDoc = docs.find((doc) => location.pathname === `/docs/${doc.slug}`)
    const pageName = activeDoc?.title || (location.pathname === '/api' ? 'API Reference' : location.pathname === '/showcase' ? 'Model Showcase' : 'Documentation')
    document.title = `${pageName} — Shyne Creator`
  }, [location.pathname])

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault(); setSearchOpen(true)
      }
      if (event.key === 'Escape') { setSearchOpen(false); setMenuOpen(false) }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const results = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase('th')
    if (!normalized) return docs.slice(0, 7)
    return docs.filter((doc) => `${doc.title} ${doc.description} ${doc.category} ${doc.content}`.toLocaleLowerCase('th').includes(normalized)).slice(0, 9)
  }, [query])

  const openResult = (slug: string) => {
    navigate(`/docs/${slug}`); setSearchOpen(false); setQuery('')
  }

  return <div className={`site-shell ${isHome ? 'home-shell' : 'docs-shell'}`}>
    <header className="topbar">
      <Brand />
      <nav className="topnav" aria-label="เมนูหลัก">
        <NavLink to="/docs/overview">คู่มือ</NavLink>
        <NavLink to="/docs/blockbench-plugin">Blockbench</NavLink>
        <NavLink to="/api">API</NavLink>
        <NavLink to="/showcase">Showcase</NavLink>
      </nav>
      <div className="header-actions">
        <button className="search-button" onClick={() => setSearchOpen(true)}><Search size={16}/><span>ค้นหาเอกสาร</span><kbd>⌘ K</kbd></button>
        <a className="download-button" href={CURSEFORGE_URL} target="_blank" rel="noreferrer" aria-label="ดาวน์โหลด Shyne Creator จาก CurseForge"><Download size={17}/><span>ดาวน์โหลด</span></a>
        <a className="icon-button social-youtube" href={YOUTUBE_URL} target="_blank" rel="noreferrer" aria-label="YouTube ของ Shyne Creator"><Play size={20}/></a>
        <a className="icon-button discord-button" href={DISCORD_URL} target="_blank" rel="noreferrer" aria-label="เข้าร่วม Discord ของ Shyne Creator"><MessageCircle size={19}/></a>
        <a className="icon-button" href={CONTACT_URL} target="_blank" rel="noreferrer" aria-label="ติดต่อและแจ้งปัญหา Shyne Creator"><MessageCircle size={19}/></a>
        <a className="icon-button" href="https://github.com/seashyne/Shyne-Creator" target="_blank" rel="noreferrer" aria-label="ซอร์สโค้ด Shyne Creator"><Code2 size={19}/></a>
        <button className="icon-button mobile-menu" onClick={() => setMenuOpen(!menuOpen)} aria-label="เปิดเมนู">{menuOpen ? <X/> : <Menu/>}</button>
      </div>
    </header>

    {!isHome && <DocsSidebar open={menuOpen} />}

    <main className={isHome ? 'home-main' : 'content-main'}>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/docs/:slug" element={<DocPage />} />
        <Route path="/api" element={<ApiPage />} />
        <Route path="/showcase" element={<ShowcasePage />} />
        <Route path="*" element={<Navigate to="/docs/overview" replace />} />
      </Routes>
    </main>

    {searchOpen && <div className="search-overlay" onMouseDown={() => setSearchOpen(false)}>
      <div className="search-modal" onMouseDown={(event) => event.stopPropagation()} role="dialog" aria-modal="true" aria-label="ค้นหาเอกสาร">
        <div className="search-input"><Search/><input autoFocus value={query} onChange={(event) => setQuery(event.target.value)} placeholder="ค้นหา Standard, Lua, Cloud..."/><kbd>ESC</kbd></div>
        <div className="search-results">
          {results.length ? results.map((doc) => { const Icon = iconMap[doc.icon]; return <button key={doc.slug} onClick={() => openResult(doc.slug)}><Icon size={18}/><span><b>{doc.shortTitle}</b><small>{doc.description}</small></span><ArrowRight size={16}/></button> }) : <p className="empty-search">ไม่พบหัวข้อที่ค้นหา</p>}
        </div>
      </div>
    </div>}
  </div>
}

function DocsSidebar({ open }: { open: boolean }) {
  return <aside className={`sidebar ${open ? 'open' : ''}`}>
    <Link to="/docs/installation" className="version-pill"><span></span> {version} <ChevronRight size={14}/></Link>
    <nav aria-label="สารบัญเอกสาร">
      {categories.map((category) => <section key={category}>
        <h3>{category}</h3>
        {docs.filter((doc) => doc.category === category).map((doc) => {
          const Icon = iconMap[doc.icon]
          return <NavLink key={doc.slug} to={`/docs/${doc.slug}`}><Icon size={14}/>{doc.shortTitle}</NavLink>
        })}
      </section>)}
    </nav>
    <div className="sidebar-foot"><img src="shyne-icon.png" alt=""/><span>Shyne Creator · MPL-2.0</span></div>
  </aside>
}

function HomePage() {
  const paths = [
    { icon: Gamepad2, eyebrow: 'PLAYER START', title: 'ใช้ Avatar', text: 'ติดตั้ง เปิดคลัง ดาวน์โหลดหรือเพิ่ม Avatar แล้วเลือกใช้ในเกม', color: 'cyan', href: '/docs/player-quickstart' },
    { icon: Box, eyebrow: 'MODEL FIRST', title: 'สร้างด้วย Blockbench', text: 'รู้จักหน้าจอ ทำโมเดล ใส่ Texture และ Export โดยไม่ต้องเขียน Lua', color: 'lime', href: '/docs/blockbench-plugin' },
    { icon: Wrench, eyebrow: 'SERVER & PACKS', title: 'สร้าง Content Pack', text: 'ต่อยอด skill, item และระบบเกมสำหรับโลกหรือเซิร์ฟเวอร์', color: 'violet', href: '/docs/creator-sdk' },
  ]
  return <>
    <div className="announcement"><span>เริ่มที่นี่</span> ติดตั้งแล้วแต่ยังใช้ไม่เป็น? <Link to="/docs/player-quickstart">เปิดคู่มือ 1 นาที <ArrowRight size={14}/></Link></div>
    <section className="hero">
      <div className="hero-copy">
        <p className="overline">PLAYER & CREATOR DOCUMENTATION <i></i></p>
        <h1>ติดตั้งแล้ว<br/>เริ่มใช้ได้เลย<span>.</span></h1>
        <p className="lead">Shyne Creator 2.12.5 คือระบบ Avatar และ Content Pack สำหรับ Minecraft 26.3 บน Fabric และ NeoForge—จัดการ Avatar ในเครื่อง สำรองขึ้น Cloud และค้นหา Avatar ที่เผยแพร่สาธารณะได้จากในเกม</p>
        <div className="hero-actions"><Link className="primary" to="/docs/player-quickstart">เริ่มใช้ Shyne <ArrowRight size={18}/></Link><a className="download-cta" href={CURSEFORGE_URL} target="_blank" rel="noreferrer"><Download size={18}/> ดาวน์โหลด</a><Link className="secondary" to="/docs/installation"><BookOpen size={18}/> วิธีติดตั้ง</Link></div>
        <div className="hero-note"><Check size={16}/><span>เลือกใช้ Avatar จากไฟล์หรือ ZIP, สำรอง/กู้คืนด้วย Cloud และดาวน์โหลด Public Avatar ได้หลังลงชื่อเข้าใช้ Minecraft</span></div>
        <div className="compat"><span>VERSION</span><b>{version}</b><i></i><b>MINECRAFT {minecraftVersion}</b><small>JAVA 25</small></div>
      </div>
      <div className="hero-visual">
        <Suspense fallback={<div className="model-viewer model-fallback"><img src="shyne-icon.png" alt=""/><span>กำลังเตรียมตัวอย่าง 3D</span></div>}><ModelViewer /></Suspense>
      </div>
    </section>
    <section className="path">
      <div className="section-heading"><div><p>CHOOSE YOUR PATH</p><h2>เริ่มในแบบที่เหมาะกับคุณ</h2></div><span>01 / GET STARTED</span></div>
      <div className="card-grid">{paths.map(({ icon: Icon, eyebrow, title, text, color, href }, index) => <Link to={href} className={`path-card ${color}`} key={title}>
        <div className="card-top"><span className="card-icon"><Icon/></span><small>0{index + 1}</small></div><p>{eyebrow}</p><h3>{title}</h3><div className="rule"></div><span>{text}</span><b>เปิดคู่มือ <ArrowRight size={16}/></b>
      </Link>)}</div>
    </section>
    <section className="quickstart">
      <div className="quick-copy"><p className="overline">AFTER INSTALLATION <i></i></p><h2>จากดาวน์โหลด<br/><span>ไปถึง Avatar แรก</span></h2>
        <ol><li><b>01</b><div><strong>เปิดคลัง Avatar</strong><p>เข้าโลกแล้วกด H หรือเลือก Esc → อวตาร</p></div></li><li><b>02</b><div><strong>เพิ่ม Avatar</strong><p>กดเปิดโฟลเดอร์ เพิ่มไฟล์ Avatar แล้วกดสแกนไฟล์</p></div></li><li><b>03</b><div><strong>เลือกใช้และทดลอง</strong><p>กดเลือกใช้ แล้วกด G เพื่อเปิดท่าทางหรือคำสั่งของ Avatar</p></div></li></ol>
        <Link className="text-link" to="/docs/player-quickstart">อ่านวิธีใช้พร้อมแก้ปัญหา <ArrowRight size={15}/></Link>
      </div>
      <div className="code-window starter-panel"><div className="window-bar"><div><i/><i/><i/></div><span>ปุ่มเริ่มต้น</span><small>PLAYER QUICKSTART</small></div><div className="starter-shortcuts">
        <div><kbd>H</kbd><span><b>คลัง Avatar</b><small>ค้นหา เลือก และเปลี่ยน Avatar</small></span></div>
        <div><kbd>G</kbd><span><b>คำสั่ง Avatar</b><small>เปิดท่าทาง สีหน้า และคำสั่งที่มี</small></span></div>
        <div><kbd>O</kbd><span><b>ตั้งค่า Shyne</b><small>ปรับหน้าจอ Cloud และการตั้งค่าขั้นสูง</small></span></div>
      </div><div className="code-status"><span><Check size={14}/> MOD READY</span><small>NEXT · CHOOSE AN AVATAR</small></div></div>
    </section>
    <Footer />
  </>
}

function DocPage() {
  const { slug } = useParams()
  const doc = docs.find((entry) => entry.slug === slug)
  if (!doc) return <Navigate to="/docs/overview" replace />
  const index = docs.indexOf(doc)
  const Icon = iconMap[doc.icon]
  const headings = extractHeadings(doc.content)
  const body = doc.content.replace(/^# .+\r?\n/, '')
  return <div className="doc-layout">
    <article className="doc-article">
      <div className="doc-breadcrumb"><Link to="/docs/overview">คู่มือ</Link><ChevronRight size={13}/><span>{doc.category}</span></div>
      <header className="doc-header"><span className="doc-icon"><Icon/></span><div><p>{doc.api ? 'SHYNE API REFERENCE' : 'SHYNE CREATOR GUIDE'}</p><h1>{doc.title}</h1><span>{doc.description}</span></div></header>
      <div className="doc-meta"><span><Activity size={14}/> อ้างอิงจากซอร์ส Shyne {version}</span><a href="https://github.com/seashyne/Shyne-Creator" target="_blank" rel="noreferrer">ดูซอร์ส <ExternalLink size={13}/></a></div>
      {doc.slug === 'first-avatar' && <QuickstartOverview />}
      <MarkdownContent content={body} />
      <nav className="doc-pagination">
        {index > 0 ? <Link to={`/docs/${docs[index - 1].slug}`}><small><ArrowLeft size={13}/> ก่อนหน้า</small><b>{docs[index - 1].shortTitle}</b></Link> : <span/>}
        {index < docs.length - 1 && <Link className="next" to={`/docs/${docs[index + 1].slug}`}><small>ถัดไป <ArrowRight size={13}/></small><b>{docs[index + 1].shortTitle}</b></Link>}
      </nav>
    </article>
    <aside className="toc"><h3>ในหน้านี้</h3>{headings.map((heading) => <button type="button" className={`level-${heading.level}`} key={`${heading.id}-${heading.text}`} onClick={() => scrollToSection(heading.id)}>{heading.text}</button>)}<div className="toc-help"><Sparkles size={15}/><b>ติดขัดตรงไหน?</b><span>ตรวจตัวอย่างและ permission ที่หัวข้อ Showcase</span><Link to="/showcase">ดูตัวอย่าง</Link></div></aside>
  </div>
}

function QuickstartOverview() {
  const steps = [
    { number: '01', title: 'เลือกประเภท', text: 'ของเสริมหรือเต็มตัว', icon: Box, target: '1-เลือกประเภท-avatar' },
    { number: '02', title: 'ติดตั้ง Plugin', text: 'ตั้งค่าใน Blockbench', icon: Wrench, target: '2-ติดตั้ง-blockbench-plugin' },
    { number: '03', title: 'ทำโมเดล', text: 'กำหนดจุดยึดให้ถูก', icon: Layers, target: '3-สร้างโมเดลและกำหนดจุดยึด' },
    { number: '04', title: 'Export', text: 'รับแพ็ก ZIP พร้อมใช้', icon: Download, target: '4-export-เป็นแพ็ก-shyne' },
    { number: '05', title: 'เข้าเกม', text: 'ติดตั้งและทดสอบ', icon: Play, target: '5-ใส่เกมและทดสอบ' },
  ]
  return <section className="quick-guide-overview" aria-label="เส้นทางสร้าง Avatar แรก">
    <div className="quick-guide-head"><div><span>เริ่มตรงนี้</span><h2>Avatar แรกใน 5 ขั้น</h2><p>ทำตามลำดับนี้ได้เลย งานแรกไม่ต้องเขียน Lua</p></div><b>ประมาณ 10–15 นาที</b></div>
    <div className="quick-guide-grid">{steps.map(({ number, title, text, icon: StepIcon, target }) => <button type="button" onClick={() => scrollToSection(target)} key={number}>
      <span><StepIcon size={17}/><small>{number}</small></span><strong>{title}</strong><p>{text}</p>
    </button>)}</div>
  </section>
}

function scrollToSection(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function ApiPage() {
  const apiDocs = docs.filter((doc) => doc.api)
  return <section className="listing-page"><div className="listing-hero"><p>SHYNE CREATOR / API</p><h1>API ที่สร้างมาเพื่อ<br/><span>Creator</span></h1><p>ข้อมูลอ้างอิงจากมาตรฐานและซอร์สใน Shyne Creator {version} โดยตรง</p></div>
    <div className="api-grid">{apiDocs.map((doc) => { const Icon = iconMap[doc.icon]; return <Link to={`/docs/${doc.slug}`} className="api-card" key={doc.slug}><span><Icon/></span><div><small>{doc.category}</small><h2>{doc.title}</h2><p>{doc.description}</p><b>อ่าน API <ArrowRight size={14}/></b></div></Link> })}</div><Footer/></section>
}

function ShowcasePage() {
  return <section className="listing-page showcase-page">
    <section className="showcase-hero">
      <div className="showcase-hero-copy">
        <p className="overline">AVATAR SHOWCASE <i></i></p>
        <h1>โหลดแล้ว<br/><span>ใช้ในเกมได้เลย.</span></h1>
        <p>Avatar ZIP ทุกแพ็กด้านล่างมี <code>avatar.json</code>, โมเดล และไฟล์ที่จำเป็นครบสำหรับ Shyne Creator — ดาวน์โหลดแล้ววางในโฟลเดอร์ Avatar ได้ทันที</p>
        <div className="showcase-hero-actions">
          <a className="showcase-primary-action" href="#avatars"><Download size={18}/> ดาวน์โหลด Avatar ZIP</a>
          <a className="showcase-discord-action" href={DISCORD_URL} target="_blank" rel="noreferrer"><MessageCircle size={18}/> เข้า Discord</a>
        </div>
        <p className="showcase-hero-note"><Check size={16}/> วางไฟล์ ZIP ไว้ที่ <code>.minecraft/shyne-mods/avatars/</code> แล้วเปิดคลัง Avatar เพื่อสแกนและเลือกใช้</p>
      </div>
      <div className="showcase-hero-visual" aria-label="ตัวอย่างโมเดล Deep Shynecore แบบสามมิติ">
        <Suspense fallback={<div className="model-viewer model-fallback"><img src="shyne-icon.png" alt=""/><span>กำลังเตรียมตัวอย่าง 3D</span></div>}><ModelViewer /></Suspense>
      </div>
    </section>

    <section className="showcase-catalog" id="avatars" aria-labelledby="avatars-heading">
      <div className="showcase-section-head"><div><p>VERIFIED AVATAR PACKS</p><h2 id="avatars-heading">Avatar ที่ติดตั้งได้จริง</h2><span>ทุกแพ็กผ่านตัวตรวจสอบ Avatar ของ Shyne Creator แล้ว ปุ่มหลักเป็น ZIP ที่นำไปวางในเกมได้โดยตรง ส่วน <code>.bbmodel</code> มีไว้สำหรับเปิดแก้ไขใน Blockbench</span></div><b>{showcaseAvatars.length} AVATARS</b></div>
      <div className="model-showcase-grid">{showcaseAvatars.map((avatar, index) => <article className="model-showcase-card" key={avatar.id}>
        <div className="model-card-art"><span>0{index + 1}</span><Box size={32}/><i></i></div>
        <div className="model-card-copy"><p>{avatar.label}</p><h3>{avatar.title}</h3><span>{avatar.description}</span></div>
        <div className="model-tags">{avatar.tags.map((tag) => <small key={tag}>{tag}</small>)}</div>
        <div className="model-downloads">
          <a className="model-download primary-download" href={siteAsset(avatar.avatar)} download><Download size={16}/> ดาวน์โหลด Avatar ZIP</a>
          <a className="model-download source-download" href={siteAsset(avatar.model)} download><FileCode2 size={16}/> ไฟล์ .bbmodel</a>
        </div>
      </article>)}</div>
    </section>

    <section className="showcase-install" aria-labelledby="install-heading"><div><p>INSTALL IN THREE STEPS</p><h2 id="install-heading">ดาวน์โหลดแล้ว<br/><span>ติดตั้งตามนี้</span></h2></div><ol><li><b>01</b><span><strong>ดาวน์โหลด ZIP</strong><small>เลือก Avatar ที่ต้องการจากด้านบน</small></span></li><li><b>02</b><span><strong>วางในโฟลเดอร์ Avatar</strong><small><code>.minecraft/shyne-mods/avatars/</code></small></span></li><li><b>03</b><span><strong>สแกนแล้วเลือกใช้</strong><small>เปิดคลัง Avatar ในเกม แล้วกดสแกนไฟล์</small></span></li></ol><Link to="/docs/player-quickstart">ดูวิธีติดตั้งละเอียด <ArrowRight size={16}/></Link></section>

    <section className="showcase-community" aria-labelledby="community-heading"><div><p>SHYNE CREATOR COMMUNITY</p><h2 id="community-heading">ติดขัดตรงไหน<br/><span>คุยกับเราได้ใน Discord</span></h2><p>ถามเรื่องโมเดล, Avatar, Blockbench หรือแบ่งปันผลงานที่กำลังสร้างอยู่กับชุมชน Shyne Creator</p></div><a href={DISCORD_URL} target="_blank" rel="noreferrer"><MessageCircle size={20}/> เข้าร่วม Discord <ExternalLink size={15}/></a></section>
    <Footer/>
  </section>
}

function MarkdownContent({ content }: { content: string }) {
  return <div className="markdown-body"><ReactMarkdown remarkPlugins={[remarkGfm]} components={{
    h1: ({ children }) => <h1 id={slugify(textFromNode(children))}>{children}</h1>,
    h2: ({ children }) => <h2 id={slugify(textFromNode(children))}>{children}</h2>,
    h3: ({ children }) => <h3 id={slugify(textFromNode(children))}>{children}</h3>,
    a: ({ href = '', children }) => {
      const markdownPath = decodeURIComponent(href.split('#')[0]).replace(/^\.\//, '').replace(/\\/g, '/')
      const file = markdownPath.split('/').pop() || ''
      const targetSlug = fileToSlug[markdownPath] ?? fileToSlug[file]
      if (targetSlug) return <Link to={`/docs/${targetSlug}`}>{children}</Link>
      if (href.startsWith('#')) return <a href={href}>{children}</a>
      return <a href={href} target="_blank" rel="noreferrer">{children}<ExternalLink size={12}/></a>
    },
    pre: ({ children }) => <CodeBlock>{children}</CodeBlock>,
    table: ({ children }) => <div className="table-wrap"><table>{children}</table></div>,
  }}>{content}</ReactMarkdown></div>
}

function CodeBlock({ children }: { children: ReactNode }) {
  const [copied, setCopied] = useState(false)
  const value = textFromNode(children).replace(/\n$/, '')
  const copy = async () => { await navigator.clipboard.writeText(value); setCopied(true); window.setTimeout(() => setCopied(false), 1200) }
  return <div className="md-code"><div><span>CODE</span><button onClick={copy}>{copied ? <Check size={14}/> : <Copy size={14}/>} {copied ? 'คัดลอกแล้ว' : 'คัดลอก'}</button></div><pre>{children}</pre></div>
}

function Footer() {
  return <footer><div><img src="shyne-icon.png" alt="Shyne Creator"/><span><b>SHYNE CREATOR</b><small>Create beyond the skin.</small></span></div><p>เอกสารสำหรับ Shyne Creator {version} · Minecraft {minecraftVersion} · MPL-2.0</p><div className="footer-links"><a href={YOUTUBE_URL} target="_blank" rel="noreferrer"><Play size={15}/> YouTube</a><a href={DISCORD_URL} target="_blank" rel="noreferrer"><MessageCircle size={14}/> Discord</a><a href={CONTACT_URL} target="_blank" rel="noreferrer"><MessageCircle size={14}/> ติดต่อ / แจ้งปัญหา</a><a href={CURSEFORGE_URL} target="_blank" rel="noreferrer">CurseForge</a><a href="https://github.com/seashyne/Shyne-Creator" target="_blank" rel="noreferrer">GitHub</a></div></footer>
}

function extractHeadings(markdown: string) {
  return markdown.split(/\r?\n/).flatMap((line) => {
    const match = /^(#{2,3})\s+(.+)$/.exec(line)
    if (!match) return []
    const text = match[2].replace(/[`*_]/g, '')
    return [{ level: match[1].length, text, id: slugify(text) }]
  })
}

function slugify(value: string) {
  return value.toLocaleLowerCase('th').trim().replace(/[“”"'`]/g, '').replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-|-$/g, '')
}

function textFromNode(node: ReactNode): string {
  if (typeof node === 'string' || typeof node === 'number') return String(node)
  if (Array.isArray(node)) return node.map(textFromNode).join('')
  if (node && typeof node === 'object' && 'props' in node) return textFromNode((node as { props: { children?: ReactNode } }).props.children)
  return ''
}

export function App() { return <HashRouter><AppShell /></HashRouter> }
