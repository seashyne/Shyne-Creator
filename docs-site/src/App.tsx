import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { HashRouter, Link, NavLink, Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  Activity, ArrowLeft, ArrowRight, BookOpen, Box, Boxes, Braces, Check, ChevronDown,
  ChevronRight, Cloud, Code2, Copy, Download, ExternalLink, FileCode2, Gamepad2,
  Layers, Menu, Palette, Play, Search, ShieldCheck, Sparkles, Users, Workflow, Wrench, X, Zap,
  type LucideIcon,
} from 'lucide-react'
import { docs, exampleDocs, fileToSlug, version, type DocIcon, type DocItem } from './content'

const iconMap: Record<DocIcon, LucideIcon> = {
  book: BookOpen, download: Download, sparkles: Sparkles, box: Box, play: Play,
  layers: Layers, braces: Braces, palette: Palette, gamepad: Gamepad2, cloud: Cloud,
  shield: ShieldCheck, users: Users, wrench: Wrench, workflow: Workflow,
}

const categories = ['เริ่มต้น', 'สร้าง Avatar', 'API และระบบ', 'เผยแพร่และพัฒนา'] as const

const avatarCode = `{
  "standard": "2.0",
  "name": "My Avatar",
  "profile": "accessory",
  "model": "model.bbmodel",
  "behavior": {
    "preset": "auto",
    "autoplay": ["Idle"]
  }
}`

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
    const pageName = activeDoc?.title || (location.pathname === '/api' ? 'API Reference' : location.pathname === '/showcase' ? 'Creator Showcase' : 'Documentation')
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
        <NavLink to="/api">API</NavLink>
        <NavLink to="/showcase">ตัวอย่าง</NavLink>
      </nav>
      <div className="header-actions">
        <button className="search-button" onClick={() => setSearchOpen(true)}><Search size={16}/><span>ค้นหาเอกสาร</span><kbd>⌘ K</kbd></button>
        <a className="icon-button" href="https://github.com/seashyne/ShyneCore" target="_blank" rel="noreferrer" aria-label="ซอร์สโค้ด Shyne Creator"><Code2 size={19}/></a>
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
  const [copied, setCopied] = useState(false)
  const copyCode = async () => {
    await navigator.clipboard.writeText(avatarCode); setCopied(true); window.setTimeout(() => setCopied(false), 1400)
  }
  const paths = [
    { icon: Box, eyebrow: 'MODEL FIRST', title: 'สร้างด้วย Blockbench', text: 'เริ่มจากโมเดลและแอนิเมชัน งานทั่วไปไม่ต้องเขียน Lua', color: 'lime', href: '/docs/first-avatar' },
    { icon: Braces, eyebrow: 'SCRIPTABLE', title: 'ต่อยอดด้วย Lua', text: 'ควบคุม procedural rig, interaction และ logic ขั้นสูง', color: 'violet', href: '/docs/lua-api' },
    { icon: Zap, eyebrow: 'MULTI-LOADER', title: 'Fabric + NeoForge', text: 'ใช้มาตรฐานเดียวกันบน Minecraft 26.2 ทั้งสอง Loader', color: 'cyan', href: '/docs/installation' },
  ]
  return <>
    <div className="announcement"><span>NEW</span> Shyne Creator {version} พร้อม Standard 2.0 <Link to="/docs/overview">ดูรายละเอียด <ArrowRight size={14}/></Link></div>
    <section className="hero">
      <div className="hero-copy">
        <p className="overline">OFFICIAL CREATOR DOCUMENTATION <i></i></p>
        <h1>รูปร่างใหม่<br/>ให้ตัวตนของคุณ<span>.</span></h1>
        <p className="lead">สร้าง Avatar, custom item, skill, power และระบบต่อสู้ด้วย Blockbench และ Lua พร้อมใช้งาน Multiplayer บน Fabric และ NeoForge</p>
        <div className="hero-actions"><Link className="primary" to="/docs/first-avatar">เริ่มสร้าง Avatar <ArrowRight size={18}/></Link><Link className="secondary" to="/docs/standard-2"><BookOpen size={18}/> Standard 2.0</Link></div>
        <div className="compat"><span>VERSION</span><b>{version}</b><i></i><b>MINECRAFT 26.2</b><small>JAVA 25</small></div>
      </div>
      <div className="hero-visual" aria-hidden="true">
        <div className="orbit orbit-one"></div><div className="orbit orbit-two"></div>
        <img className="hero-logo" src="shyne-logo.png" alt="" />
        <div className="voxel-head"><div className="face"><i/><i/></div><div className="ear left"/><div className="ear right"/></div>
        <span className="tag tag-model">MODEL<br/><b>READY</b></span><span className="tag tag-zero">ZERO<br/><b>LUA</b></span>
      </div>
    </section>
    <section className="path">
      <div className="section-heading"><div><p>CHOOSE YOUR PATH</p><h2>เริ่มในแบบที่เหมาะกับคุณ</h2></div><span>01 / GET STARTED</span></div>
      <div className="card-grid">{paths.map(({ icon: Icon, eyebrow, title, text, color, href }, index) => <Link to={href} className={`path-card ${color}`} key={title}>
        <div className="card-top"><span className="card-icon"><Icon/></span><small>0{index + 1}</small></div><p>{eyebrow}</p><h3>{title}</h3><div className="rule"></div><span>{text}</span><b>เปิดคู่มือ <ArrowRight size={16}/></b>
      </Link>)}</div>
    </section>
    <section className="quickstart">
      <div className="quick-copy"><p className="overline">YOUR FIRST AVATAR <i></i></p><h2>เริ่มต้นใน<br/><span>สามขั้นตอน</span></h2>
        <ol><li><b>01</b><div><strong>สร้างโปรเจกต์</strong><p>สร้างโฟลเดอร์ Avatar และเปิดโมเดลด้วย Blockbench</p></div></li><li><b>02</b><div><strong>กำหนดพฤติกรรม</strong><p>เลือก profile และ animation ใน avatar.json</p></div></li><li><b>03</b><div><strong>ตรวจแล้วเข้าเกม</strong><p>Validate แพ็กก่อนนำไปใช้กับ Shyne Creator</p></div></li></ol>
        <Link className="text-link" to="/docs/first-avatar">อ่าน Quickstart ฉบับเต็ม <ArrowRight size={15}/></Link>
      </div>
      <div className="code-window"><div className="window-bar"><div><i/><i/><i/></div><span>avatar.json</span><button onClick={copyCode}>{copied ? <Check size={15}/> : <Copy size={15}/>} {copied ? 'คัดลอกแล้ว' : 'คัดลอก'}</button></div><pre><code>{avatarCode}</code></pre><div className="code-status"><span><Check size={14}/> VALID MANIFEST</span><small>SHYNE STANDARD 2.0</small></div></div>
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
      <div className="doc-meta"><span><Activity size={14}/> อ้างอิงจากซอร์ส Shyne {version}</span><a href="https://github.com/seashyne/ShyneCore" target="_blank" rel="noreferrer">ดูซอร์ส <ExternalLink size={13}/></a></div>
      <MarkdownContent content={body} />
      <nav className="doc-pagination">
        {index > 0 ? <Link to={`/docs/${docs[index - 1].slug}`}><small><ArrowLeft size={13}/> ก่อนหน้า</small><b>{docs[index - 1].shortTitle}</b></Link> : <span/>}
        {index < docs.length - 1 && <Link className="next" to={`/docs/${docs[index + 1].slug}`}><small>ถัดไป <ArrowRight size={13}/></small><b>{docs[index + 1].shortTitle}</b></Link>}
      </nav>
    </article>
    <aside className="toc"><h3>ในหน้านี้</h3>{headings.map((heading) => <a className={`level-${heading.level}`} key={`${heading.id}-${heading.text}`} href={`#${heading.id}`}>{heading.text}</a>)}<div className="toc-help"><Sparkles size={15}/><b>ติดขัดตรงไหน?</b><span>ตรวจตัวอย่างและ permission ที่หัวข้อ Showcase</span><Link to="/showcase">ดูตัวอย่าง</Link></div></aside>
  </div>
}

function ApiPage() {
  const apiDocs = docs.filter((doc) => doc.api)
  return <section className="listing-page"><div className="listing-hero"><p>SHYNE CREATOR / API</p><h1>API ที่สร้างมาเพื่อ<br/><span>Creator</span></h1><p>ข้อมูลอ้างอิงจากมาตรฐานและซอร์สใน Shyne Creator {version} โดยตรง</p></div>
    <div className="api-grid">{apiDocs.map((doc) => { const Icon = iconMap[doc.icon]; return <Link to={`/docs/${doc.slug}`} className="api-card" key={doc.slug}><span><Icon/></span><div><small>{doc.category}</small><h2>{doc.title}</h2><p>{doc.description}</p><b>อ่าน API <ArrowRight size={14}/></b></div></Link> })}</div><Footer/></section>
}

function ShowcasePage() {
  const [active, setActive] = useState(0)
  return <section className="listing-page showcase-page"><div className="listing-hero"><p>REAL CREATOR FIXTURES</p><h1>เรียนรู้จาก<br/><span>ตัวอย่างจริง</span></h1><p>ตัวอย่างเหล่านี้มาจากชุดทดสอบ Creator ในโปรเจกต์ Shyne และไม่ถูกบรรจุใน Mod JAR</p></div>
    <div className="showcase-grid">{exampleDocs.map((example, index) => <button key={example.title} onClick={() => setActive(index)} className={active === index ? 'active' : ''}><span>0{index + 1}</span><FileCode2/><h2>{example.title}</h2><p>{example.description}</p><small>PERMISSION · {example.permission}</small><ChevronRight/></button>)}</div>
    <div className="example-detail">{exampleDocs[active].image && <img src={exampleDocs[active].image} alt="ภาพตัวอย่าง Integration Avatar ของ Shyne"/>}<div><p className="overline">EXAMPLE 0{active + 1} <i></i></p><MarkdownContent content={exampleDocs[active].content} /></div></div><Footer/></section>
}

function MarkdownContent({ content }: { content: string }) {
  return <div className="markdown-body"><ReactMarkdown remarkPlugins={[remarkGfm]} components={{
    h1: ({ children }) => <h1 id={slugify(textFromNode(children))}>{children}</h1>,
    h2: ({ children }) => <h2 id={slugify(textFromNode(children))}>{children}</h2>,
    h3: ({ children }) => <h3 id={slugify(textFromNode(children))}>{children}</h3>,
    a: ({ href = '', children }) => {
      const file = href.split('/').pop() || ''
      const targetSlug = fileToSlug[file]
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
  return <footer><div><img src="shyne-icon.png" alt="Shyne Creator"/><span><b>SHYNE CREATOR</b><small>Create beyond the skin.</small></span></div><p>เอกสารสำหรับ Shyne Creator {version} · Minecraft 26.2 · MPL-2.0</p><div><a href="https://www.curseforge.com/minecraft/mc-mods/shyne-creator" target="_blank" rel="noreferrer">CurseForge</a><a href="https://github.com/seashyne/ShyneCore" target="_blank" rel="noreferrer">GitHub</a></div></footer>
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
