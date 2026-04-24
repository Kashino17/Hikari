'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'

export default function Variant3Root() {
  const router = useRouter()
  useEffect(() => { router.replace('/variant-3/feed') }, [router])
  return null
}
